(ns dithcord.core
  (:require [clojure.core.async :refer [<! <!! >! go-loop thread timeout chan close! put! alt!]]
            [cheshire.core :as json]
            [http.async.client :as http]))

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

(defn shutdown [session]
  (do
    (if (some? (:ws @session))
      (http/close (:ws @session)))
    (if (some? (:ping-timer @session))
      (close! (:ping-timer @session)))
    (swap! session nil)))

(defn ping-pong [delay session]
  (do (prn "Starting PING")
      (let [next-id #(swap! (get @session :ping-counter) inc)
            out-chan (get @session :chan-out)
            timer (set-interval #(put! out-chan {:op 1 :d (next-id)}) delay)]
        (swap! session assoc :ping-timer timer)))
  )

(defn on-ws-open [ws session]
  (prn "Connected to Discord API Websocket!"))

(defn on-ws-close [ws status reason session]
  (do
    (prn (format "Connection closed [%s] : %s" status reason))
    (shutdown session)
    ))

(defn on-ws-error [ws error session]
  (do
    (prn (str "Error Occured: " error))
    (shutdown session)
    )
  )

(defn api-request [session, method, url, data, file])

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

(defn handle-hello [session msg]
  (when (= 10 (:op msg))
    (let [out-chan (get @session :chan-out)
          identify-packet (identify (get @session :token))]
      (prn "Executing Handle Hello")
      (put! out-chan identify-packet)
      (ping-pong (-> msg :d :heartbeat_interval) session)
      )))

(defn handle-ready [session msg]
  (when (and (= 0 (:op msg)) (= "READY" (:t msg)))
    (prn (str "Handle Ready on " (:op msg)))
    (swap! session assoc :session-id (-> msg :d :session_id))))

(defn handle-dispatch [session msg]
  ; This handler dispatches events to the client's handler functions.
  (when (= 0 (:op msg))
    (prn (str "Handle Dispatch on " (:op msg)))
    (let [func (get-in @session [:handlers (:t msg)])]
      (do (prn func)
          (if (fn? func)
            (func session)))
        )
    ))

(def internal-handlers
  [handle-ready
   handle-hello
   handle-dispatch
   ])

(defn on-message [ws msg session]
  (prn msg)
    (prn (str "[OP][" (:op msg) "][EVENT][" (:t msg) "]"))
    (doall (map #(apply % [session msg]) (:internal-handlers @session))))

(defn connect-raw [state]
  (let [session (atom state)
        client (http/create-client :follow-redirects true)
        ws (http/websocket client
                           "wss://gateway.discord.gg/?v=6&encoding=json"
                           :open #(on-ws-open % session)
                           :close #(on-ws-close %1 %2 %3 session)
                           :error #(on-ws-error %1 %2 session)
                           :text #(on-message %1 (json/parse-string %2 true) session))
        out-channel (chan)]
    (swap! session assoc :socket ws)
    (swap! session assoc :chan-out out-channel)
    (swap! session assoc :ping-counter (atom 0))
    (go-loop []
      (let [m (<! out-channel)
            s (json/generate-string m)]
        (prn "Outputting Message to Socket: " s)
        (http/send ws :text s)
        (recur)))
    session
    ))

(defn connect [state]
  (connect-raw
    (merge state
           {:internal-handlers internal-handlers})))