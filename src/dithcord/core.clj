(ns dithcord.core
  (:require [clojure.core.async :refer [<! <!! >! go-loop thread timeout chan close! put!]]
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
   :channel             #(str ((:channels endpoints) "/" %1))
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

(def core-out (chan))

(comment
  (if (not kill-chan)
    (let [kill-chan (chan)]
      (go-loop []
                     (let [[event ch] (alts! [input-chan kill-chan])]
                       (if (= ch kill-chan)
                         (close! kill-chan)
                         (do
                           (process-event! event events)
                           (recur)))))
      (assoc this :kill-chan kill-chan))
    this)
  )

(defn ping-pong [out-pipe delay session]
  (let [counter (atom 0)
        next-id #(swap! counter inc)]
    (go-loop []
      (<! (timeout delay))
      ;(println "Sending a ping request")
      (>! out-pipe {:op 1 :d (next-id)} )
     (recur))))

(defn on-ws-open [ws session]
  (prn "Connected to Discord API Websocket!"))

(defn on-ws-close [ws status reason session]
  (do
    (prn (format "Connection closed [%s] : %s" status reason))
    ;(shutdown)
    ))

(defn on-ws-error [ws error session]
  (prn (str "Error Occured: " error) ))

(defn ws-message [ws m session]
  (let [msg (json/parse-string m true)
        op (-> msg :op)]
    ;(println op)
    ;(println msg)
    (case op
      0 (let [action (-> msg :t)]
          ; Presumably, this will call the functions sent by the client!
          (case action
            "READY" (do
                      (swap! session assoc :session-id (-> msg :d :session_id))
                      (str "Got Ready Packet, session ID: " (get @session :session-id))
                      (let [func (get-in @session [:handlers :READY])]
                        (func session)))
            "MESSAGE_CREATE" (let [func (get-in @session [:handlers :MESSAGE_CREATE])]
                               (func session (-> msg :d)))
            "GUILD_CREATE" (comment "Not Yet Implemented!")
            "TYPING_START" (let [func (get-in @session [:handlers :TYPING_START])]
                             ;(prn (-> msg :d))
                             (func session (-> msg :d :user_id) (-> msg :d :channel_id))
                             )
            (println msg)
            ))
      10 (do
           (ping-pong core-out (-> msg :d :heartbeat_interval) session)
           ;(println "Received OP Code 10, sending Token")
           (put! core-out (identify (get @session :token))))
      11 (comment "HEARTBEAT_ACK RECEIVED")
      ;(prn (str "Received OP code " op))
      )))

(defn send-message [session msg channel]
  (prn (str "Received send-message command on " channel " : " msg))
  (let [client (http/create-client :follow-redirects true)
        resp (http/POST
               client
               (str "https://discordapp.com/api/v6/channels/" channel "/messages")
               :body {:content msg}
               :headers {"Authorization" (str "Bot " (get @session :token))
                        "Content-type" "application/x-www-form-urlencoded"
                        "Content-length" 13})]
    resp)
)

(defn make-socket [url session]
  (let [client (http/create-client :follow-redirects true)
        ws (http/websocket client
                           url
                           :open #(on-ws-open % session)
                           :close #(on-ws-close %1 %2 %3 session)
                           :error #(on-ws-error %1 %2 session)
                           :text #(ws-message %1 %2 session))]
    (go-loop []
      (let [m (<! core-out)
            s (json/generate-string m)]
        ;(println (str "Sending message: " m))
        (http/send ws :text s)
        (recur)))
    ws))

(defn connect [token handlers]
  "Todo: Get URL from API address..."
  (let [url "wss://gateway.discord.gg/?v=6&encoding=json"
        session (atom {:token token :handlers handlers})
        ws (make-socket url session)]
    (swap! session assoc :socket ws)
    session
    ))
