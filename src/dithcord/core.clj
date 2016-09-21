(ns dithcord.core
  (:require [clojure.core.async :refer [<! <!! >! go-loop thread timeout chan close! put!]]
    ;[clj-http.client :as http]
            [gniazdo.core :as ws]
            [cheshire.core :as json]
            [http.async.client :as http]))

(def API "https://discordapp.com/api/v6")
(def CDN "https://cdn.discordapp.com")

(def endpoints
  {:login               (str API "/auth/login")
   :logout              (str API "/auth/logout")
   :gateway             (str API "/gateway")
   :invite              (str API "/invite" :id)
   :CDN                 (str API "/auth/login")

   :user                #(str API "/users/" %1)
   :user-channels       #(str ((:user endpoints) %1) "/channels")
   :avatar              #(str ((:user endpoints) %1) "/avatars/" %2 ".jpg")
   :user-profile        (str API "/users/@me")
   :user-guilds         #(str ((:user-profile endpoints) "/guilds/" %1))

   :guilds              (str API "/guilds")
   :guild               #(str (:guilds endpoints) "/" %)
   :guild-icon          #(str ((:guild endpoints) %1) "/icons/" %2 ".jpg")
   :guild-prune         #(str ((:guild endpoints) %1) "/prune")
   :guild-embed         #(str ((:guild endpoints) %1) "/embed")
   :guild-invites       #(str ((:guild endpoints) %1) "/invites")
   :guild-roles         #(str ((:guilds endpoints) %1) "/roles")
   :guild-role          #(str ((:guilds-roles endpoints) %1) "/" %2)
   :guild-bans          #(str ((:guild endpoints) %1) "/bans")
   :guild-integrations  #(str ((:guild endpoints) %1) "/integrations")
   :guild-members       #(str ((:guild endpoints) %1) "/members")
   :guild-member        #(str ((:guilds-members endpoints) %1) "/" %2)
   :guild-member-nick   #(str ((:guild-member endpoints) %1 "@me") "/nick")
   ;WTF is this endpoint just for nickname, guys?

   :channels            (str API "/channels")
   :channel             #(str ((:channels endpoints) %1))
   :channel-messages    #(str ((:channel endpoints) %1) "/messages")
   :channel-message     #(str ((:channel-messages endpoints) %1) "/" %2)
   :channel-invites     #(str ((:channel endpoints) %1) "/invites")
   :channel-typing      #(str ((:channel endpoints) %1) "/typing")
   :channel-permissions #(str ((:channel endpoints) %1) "/permissions")})
;((:guild-icon endpoints) "the-guild-id" "the-icon-hash")


(def error-messages
  {:NO_TOKEN                  "request to use token, but token was unavailable to the client"
   :NO_BOT_ACCOUNT            "you should ideally be using a bot account!"
   :BAD_WS_MESSAGE            "a bad message was received from the websocket - bad compression or not json"
   :TOOK_TOO_LONG             "something took too long to do"
   :NOT_A_PERMISSION          "that is not a valid permission string or number"
   :INVALID_RATE_LIMIT_METHOD "unknown rate limiting method"
   :BAD_LOGIN                 "incorrect login details were provided"})


(defn identify [token]
  {:op 2
   :d {:token (str "Bot " token)
               :properties {:$os "linux"
                            :$browser "dithcord"
                            :$device "dithcord"
                            :$referrer ""
                            :$referring_domain ""}
               :compress false
               :large_threshold 250}})

(def core-in (chan))
(def core-out (chan))

(defn ping-pong [out-pipe delay]
  (let [counter (atom 0)
        next-id #(swap! counter inc)]
    (go-loop []
      (<! (timeout delay))
      ;(println "Sending a ping request")
      (>! out-pipe {:op 1 :d (next-id)} )
     (recur))))

(defn on-ws-open [ws]
  (prn "Connected to Discord API Websocket!"))

(defn on-ws-close [ws status reason]
  (do
    (prn (format "Connection closed [%s] : %s" status reason) )
    ;(shutdown)
    ))

(defn on-ws-error [ws error]
  (prn (str "Error Occured: " error) ))

(defn ws-message [ws m]
  (let [msg (json/parse-string m true)
        op (-> msg :op)]
    (println op)
    (println msg)
    (case op
      0 (let [action (-> msg :t)]
          ; Presumably, this will call the functions sent by the client!
          (case action
            "READY" (str "Got Ready Packet, session ID: " (-> msg :d :session_id))
            "MESSAGE_CREATE" (println (str "Message Received from [" (-> msg :d :author :username) "]: "  (-> msg :d :content)))
            (println msg)
            ))
      10 (do
           (ping-pong core-out (-> msg :d :heartbeat_interval))
           (println "Received OP Code 10, sending Token")
           (put! core-out (identify "MjA5MDE1MzEwNTcxNzk4NTM0.CsEAEQ.1EWIOuraD_ZX44SEn2D6FHMlEfA")))
      11 (comment "HEARTBEAT_ACK RECEIVED")
      (prn (str "Received OP code " op)))))

(defn make-socket [url token]
  (with-open [client (http/create-client)]
  (let [ws (http/websocket client
                           url
                           ;How do I pass the token to one of these functions, like :on-ws-open?
                           :open on-ws-open
                           :close on-ws-close
                           :error on-ws-error
                           :text ws-message)]
    (go-loop []
      (let [m (<! core-out)
            s (json/generate-string m)]
        (println (str "Sending message: " m))
        (http/send ws :text s)
        (recur)))
    [ws]
    ))
)

(defn connect [token]
  "Todo: Get URL from API address..."
  (let [url "wss://gateway.discord.gg/?v=6&encoding=json"
        ws (make-socket url token)]
    ; Not sure if this goes here or it **has** to be in the make-socket function...

    ))

;(connect "test")

  ;;;; OLD WEBSOCKET xD

(comment

  ; main thread?
  (thread
    (loop []
      (when-let [m (<!! core-in)]
        (let [op (:op m)]
          (println m)
          (case op
            0 (let [action (-> m :t)]
                (case action
                  "READY" (println (str "Got Ready Packet, session ID: " (-> m :d :session_id)))
                  "MESSAGE_CREATE" (println (str "Message Received from [" (-> m :d :author :username) "]: "  (-> m :d :content)))
                  (println m))
                )
            10 (do
                 (ping-pong core-out (-> m :d :heartbeat_interval))
                 (println "Received OP Code 10, sending Token")
                 (put! core-out (identify "MjA5MDE1MzEwNTcxNzk4NTM0.CsEAEQ.1EWIOuraD_ZX44SEn2D6FHMlEfA")))
            11 (comment "HEARTBEAT_ACK RECEIVED")
            (prn (str "Received OP code " op))
            ))
        (recur)))
    (println "Log Closed"))

  (defn connect-socket [url]
    (let [shutdown (fn []
                     (prn "Closing Sockets")
                     (close! core-in)
                     (close! core-out))
          socket (ws/connect url
                             :on-receive
                             (fn [m] (put! core-in (json/parse-string m true)))
                             :on-connect
                             (fn [_] (prn "Connected!"))
                             :on-error
                             (fn [e] (prn (str "Error Occured: " e) ))
                             :on-close
                             (fn [status, reason] (do
                                                    (prn (str "Connection closed [" status "] : " reason) )
                                                    (shutdown)) ))]
      (go-loop []
        (let [m (<! core-out)
              s (json/generate-string m)]
          (println (str "Sending message: " m))
          (ws/send-msg socket s)
          (recur)))
      [core-in core-out]))

  (defn start-socket []
    (let [ws-address (:url (:body (http/get (str API "/gateway") {:as :json})))]
      (connect-socket (str ws-address "/?v=6&encoding=json"))))


  (connect-socket "wss://gateway.discord.gg/?v=6&encoding=json")
  (connect-socket "ws://localhost")


  )
