(ns dithcord.core
  (:gen-class)
  (:require [cheshire.core :as json]
            [aleph.http :as http]
            [manifold.stream :as s]
            [dithcord.storage :as ds]
            [dithcord.zip :as z]
            ))

(defn identify [token]
  {:op 2
   :d {:token (str "Bot " token)
               :properties {:$os "linux"
                            :$browser "dithcord"
                            :$device "dithcord"
                            :$referrer ""
                            :$referring_domain ""}
               :compress true
               :large_threshold 250}})

(defn send-ws [session msg]
  (let [m (json/generate-string msg)
        conn (:socket @session)]
    (prn (str m))
    (prn ())
    (s/put! conn m)))

(defn shutdown [session]
  (do
    (if (some? (:socket @session))
      (s/close! (:socket @session)))
    (if (some? (:ping-timer @session))
      (future-cancel (:ping-timer @session)))
    (reset! session nil)))

(defn set-interval [callback ms]
  (future (while true (do (Thread/sleep ms) (callback)))))

(defn ping-pong [delay session]
  (do (prn "Starting PING")
      (let [next-id #(swap! (get @session :ping-counter) inc)
            timer (set-interval #(send-ws session {:op 1 :d (next-id)}) delay)]
        (swap! session assoc :ping-timer timer))))

(defn api-request [session, method, url, data, file])

(defn send-message [session msg channel]
  (prn (str "Received send-message command on " channel " : " msg))
  (let [resp @(http/post
               (str "https://discordapp.com/api/v6/channels/" channel "/messages")
               :keys {
                      :body {:content msg}
                      :headers {:Authorization (str "Bot " (get @session :token))
                                :Content-Type "application/x-www-form-urlencoded"
                                :Content-Length 13}
                      })]
    resp))

(defn handle-hello [session msg]
  (when (= 10 (msg :op))
    (let [identify-packet (identify (:token @session))]
      (prn "Executing Handle Hello")
      (send-ws session identify-packet)
      (ping-pong (-> msg :d :heartbeat_interval) session)
      )))

(defn handle-ready [session msg]
  (when (and (= 0 (msg :op)) (= "READY" (msg :t)))
    (prn (str "Handle Ready on " (msg :op)))
    (swap! session assoc :session-id (-> msg :d :session_id))))

(defn handle-dispatch [session msg]
  ; This handler dispatches events to the client's handler functions.
  (when (= 0 (msg :op))
    (prn (str "Handle Dispatch on " (msg :op) " " (msg :t)))
    (let [handler (get-in @session [:handlers (keyword (msg :t))])
          first-handler (first handler)
          ;storage-handler (ns-resolve *ns* (symbol (msg :t)))
          ]
      (when (= (msg :t) "MESSAGE_CREATE")
        (ds/MESSAGE_CREATE (msg :d)))
      #_(if-not (nil? storage-handler)
        (storage-handler (msg :d)))
      (if-not (nil? first-handler)
        (first-handler session (msg :d))
        ))))

(def internal-handlers
  [handle-ready
   handle-hello
   handle-dispatch])

(defn on-ws-close [session]
  (do
    (prn (format "WebSocket Connection closed"))
    (shutdown session)
    ))

(defn on-message [msg session]
  (let [st (if (instance? String msg)
             msg
             (String. (byte-array (z/inflate msg))))
        m (json/parse-string st true)]
    (spit "event.log"  (str m "\r\n")  :append true)
    (when-let [debug-handler (get-in @session [:handlers :debug])]
      (debug-handler session m))
    (handle-hello session m)
    (run! #(apply % [session m]) (:internal-handlers @session))
    ))


(defn ws-connect
  "Creates a websocket Connection to Discord API"
  [state]
  (let [session (atom state)
        ws @(http/websocket-client "wss://gateway.discord.gg/?v=6&encoding=json")]
    (s/consume #(on-message %1 session) ws)
    (swap! session assoc :socket ws)
    (swap! session assoc :ping-counter (atom 0))
    (s/on-closed ws #(on-ws-close session))
    session
    ))

(defn connect [state]
  (let [full-state (merge state {:internal-handlers internal-handlers})
        session (ws-connect full-state)]
    session
    ))

(defn -main
  "Dithcord Library In-Development"
  [& args]
  (prn "Dithcord Library Loaded"))