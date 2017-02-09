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

(defn identify [session]
  {:op 2
   :d {:token (str (if (@session :selfbot) "Bot ") (@session :token))
               :properties {:$os "linux"
                            :$browser "dithcord"
                            :$device "dithcord"
                            :$referrer ""
                            :$referring_domain ""}
               :compress true
               :large_threshold 250}})

(defn log [session type msg]
  (let [handler (first (get-in @session [:handlers (keyword type)]))]
    (when (some? handler)
      (handler session msg))))

(defn send-ws [session msg]
  (let [m (json/generate-string msg)
        socket (:socket @session)]
    (log session "debug"  (str "Sending Message to websocket: " m " on " socket))
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
  (do (log session "debug" "Starting PING")
      (let [next-id #(swap! (get @session :ping-counter) inc)
            timer (set-interval #(send-ws session {:op 1 :d (next-id)}) delay)]
        (swap! session assoc :ping-timer timer))))

(defn api-fetch [session part]
  (let [resp @(http/get (str "https://discordapp.com/api/v6" part)
                        {:headers {"Authorization" (str (if (-> @session :user :bot) "Bot ") (get @session :token))}})]
    (json/parse-string (bs/to-string (resp :body)) true)))

(defn api-post [session part data]
  (let [resp @(http/post
                (str "https://discordapp.com/api/v6" part)
                {:body (json/generate-string {:content data})
                 :headers {"Authorization" (str (if (-> @session :user :bot) "Bot ") (@session :token))
                           "Content-type" "application/x-www-form-urlencoded"}})]
    resp))

(defn send-message [session msg channel]
  (log session "debug"  (str "Received send-message command on " channel " : " msg))
  (api-post session (str "/channels/" channel "/messages") msg))

(defn change-nick [session guild user new-nick]
  (log session "debug" (str "Change nickname for " (user :id) " to " new-nick " on guild " (guild :id)))
  (api-post session (str "/guilds/" (guild :id) "/members/") {:nick new-nick}))

(defn handle-hello [session msg]
  (when (= 10 (msg :op))
    (let [identify-packet (identify session)]
      (log session "debug" "Authenticating to the Discord API...")
      (send-ws session identify-packet)
      (ping-pong (-> msg :d :heartbeat_interval) session)
      )))

(defn handle-ready [session msg]
  (when (and (= 0 (msg :op)) (= "READY" (msg :t)))
    (log session "debug" "Receiving Hello ")
    (swap! session assoc :session-id (-> msg :d :session_id))
    (swap! session assoc :user (-> msg :d :user))))

(defn handle-dispatch [session msg]
  ; This handler dispatches events to the client's handler functions.
  (when (= 0 (msg :op))
    (log session "debug" (str "Handling Dispatch on " (msg :op) " " (msg :t)))
    (let [handler (get-in @session [:handlers (keyword (msg :t))])
          first-handler (first handler)
          map-name (-> (str/split (msg :t) #"_") first str/lower-case)
          filters {:permission-overwrite
                   (fn [row]
                     (let [ref-key (keyword (str (:type row) "-id"))
                           ref-id (:id row)]
                       (-> row
                           (dissoc :id)
                           (assoc ref-key ref-id))))}]
      (when-not (= (msg :t) "PRESENCE_UPDATE")
        (d/transact! db/conn (db/sort-facts (db/flatten-map {(keyword map-name) [(msg :d)]} filters))))
      (when (some? first-handler)
        (log session "debug" (str "Running handler for " (msg :d)))
        (first-handler session (msg :d)))
      )))

(def internal-handlers
  [handle-ready
   handle-hello
   handle-dispatch])

(defn on-ws-close [session]
  (do
    (log session "debug"  (format "WebSocket Connection closed"))
    ;(shutdown session)
    ))

(defn on-message [msg session]
  (let [st (if (instance? String msg)
             msg
             (String. (byte-array (z/inflate msg))))
        m (json/parse-string st true)]
    #_(when-let [debug-handler (-> @session :handlers :debug)]
      (debug-handler session m))
    (spit "event.log" (str m "\r\n") :append true)
    (run! #(apply % [session m]) (:internal-handlers @session))
    ))

(defn connect
  "Creates a websocket Connection to Discord API"
  [state]
  (let [full-state (merge state {:internal-handlers internal-handlers})
        session (atom full-state)
        ws @(http/websocket-client "wss://gateway.discord.gg/?v=6&encoding=json"
                                   {:max-frame-payload 1e6
                                    :max-frame-size 2e6
                                    :max-queue-size 65556})]
    (swap! session assoc :ping-counter (atom 0))
    (swap! session assoc :socket ws)
    (s/consume #(on-message %1 session) ws)
    (s/on-closed ws #(on-ws-close session))
    session
    ))

(defn -main
  "Dithcord Library In-Development"
  [& args]
  (prn "Dithcord Library Loaded"))