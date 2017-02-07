(ns dithcord.core
  (:gen-class)
  (:require [cheshire.core :as json]
            [aleph.http :as http]
            [manifold.stream :as s]
            [dithcord.storage :as db]
            [datascript.core :as d]
            [dithcord.zip :as z]
            [clojure.string :as str]
            [byte-streams :as bs]
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
        socket (:socket @session)]
    (prn (str "Sending Message to websocket: " m " on " socket))
    (s/put! socket m)))

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

(defn api-fetch [session part]
  (let [resp @(http/get (str "https://discordapp.com/api/v6" part)
                        {:headers {"Authorization" (str "Bot " (get @session :token))}})]
    (json/parse-string (bs/to-string (resp :body)) true)))

(defn api-post [session part data]
  (let [resp @(http/post
                (str "https://discordapp.com/api/v6" part)
                {:body (json/generate-string {:content data})
                 :headers {"Authorization" (str "Bot " (@session :token))
                           "Content-type" "application/x-www-form-urlencoded"}})]
    resp))

(defn send-message [session msg channel]
  (prn (str "Received send-message command on " channel " : " msg))
  (api-post session (str "/channels/" channel "/messages") msg))

(defn change-nick [session guild user new-nick]
  (prn (str "Change nickname for " (user :id) " to " new-nick " on guild " (guild :id)))
  (api-post session (str "/guilds/" (guild :id) "/members/") {:nick new-nick}))

(defn handle-hello [session msg]
  (when (= 10 (msg :op))
    (let [identify-packet (identify (:token @session))]
      (prn "Authenticating to the Discord API...")
      (send-ws session identify-packet)
      (ping-pong (-> msg :d :heartbeat_interval) session)
      )))

(defn handle-ready [session msg]
  (when (and (= 0 (msg :op)) (= "READY" (msg :t)))
    (prn "Receiving Hello ")
    (swap! session assoc :session-id (-> msg :d :session_id))
    (swap! session assoc :user (api-fetch session "/users/@me"))))

(defn handle-dispatch [session msg]
  ; This handler dispatches events to the client's handler functions.
  (when (= 0 (msg :op))
    (prn (str "Handling Dispatch on " (msg :op) " " (msg :t)))
    (let [handler (get-in @session [:handlers (keyword (msg :t))])
          first-handler (first handler)
          map-name (-> (str/split (msg :t) #"_") first str/lower-case)]
      ;(d/transact! db/conn (db/sort-facts (db/flatten-map {(keyword map-name) [(msg :d)]})))

      (when (some? first-handler)
        (prn (str "Running handler for " (msg :d)))
        (first-handler session (msg :d)))
      )))

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
    (run! #(apply % [session m]) (:internal-handlers @session))
    ))

(defn connect
  "Creates a websocket Connection to Discord API"
  [state]
  (let [full-state (merge state {:internal-handlers internal-handlers})
        session (atom full-state)
        ws @(http/websocket-client "wss://gateway.discord.gg/?v=6&encoding=json")]
    (s/consume #(on-message %1 session) ws)
    (swap! session assoc :socket ws)
    (swap! session assoc :ping-counter (atom 0))
    (s/on-closed ws #(on-ws-close session))
    session
    ))

(defn -main
  "Dithcord Library In-Development"
  [& args]
  (prn "Dithcord Library Loaded"))