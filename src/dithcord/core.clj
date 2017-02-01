(ns dithcord.core
  (:gen-class)
  (:require [clojure.core.async :refer [<! <!! >! go-loop thread timeout chan close! put! alt!]]
            [cheshire.core :as json]
            [aleph.http :as http]
            [byte-streams :as bs]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [manifold.stream :as s]
            [dithcord.storage :as ds]
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

(defn shutdown [session]
  (do
    (if (some? (:ws @session))
      (s/close! (:ws @session)))
    (if (some? (:ping-timer @session))
      (close! (:ping-timer @session)))
    (reset! session nil)))

(defn set-interval
  [f time-in-ms]
  (let [stop (chan)]
    (go-loop []
               (alt!
                 (timeout time-in-ms)
                 (do (<! (thread (f)))
                     (recur))
                 stop :stop))
    stop))

(defn ping-pong [delay session]
  (do (prn "Starting PING")
      (let [next-id #(swap! (get @session :ping-counter) inc)
            out-chan (get @session :ws)
            timer (set-interval #(put! out-chan {:op 1 :d (next-id)}) delay)]
        (swap! session assoc :ping-timer timer))))


(defn api-request [session, method, url, data, file])

(defn send-message [session msg channel]
  (prn (str "Received send-message command on " channel " : " msg))
  #_(let [client (http/create-client :follow-redirects true)
        resp (http/POST
               client
               (str "https://discordapp.com/api/v6/channels/" channel "/messages")
               :body {:content msg}
               :headers {"Authorization" (str "Bot " (get @session :token))
                        "Content-type" "application/x-www-form-urlencoded"
                        "Content-length" 13})]
    resp))

(defn handle-hello [session msg]
  (when (= 10 (msg :op))
    (let [out-chan (get @session :chan-out)
          identify-packet (identify (get @session :token))]
      (prn "Executing Handle Hello")
      (put! out-chan identify-packet)
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
  (let [debug-handler (get-in @session [:handlers :debug])]
    (spit "event.log" (str msg "\r\n") :append true)
    (if-not (nil? debug-handler)
      (debug-handler session msg))
    (doall (map #(apply % [session msg]) (:internal-handlers @session))))
  )

(defn ws-connect
  "Creates a websocket Connection to Discord API"
  [state]
  (let [session (atom state)
        ws @(http/websocket-client "wss://gateway.discord.gg/v?6&encoding=json")]
    (s/on-closed ws #(on-ws-close session))
    (str ws)
    (swap! session assoc :socket ws)
    (swap! session assoc :ping-counter (atom 0))
    session
    ))

(defn connect [state]
  (let [full-state (merge state {:internal-handlers internal-handlers})
        session (ws-connect [full-state])]
    (s/consume #(on-message (json/parse-string %1 true) session) (:ws @session))))


(defn -main
  "Dithcord Library In-Development"
  [& args]
  (prn "Dithcord Library Loaded"))