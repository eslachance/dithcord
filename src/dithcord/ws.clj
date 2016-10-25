(ns dithcord.ws)

(defn create-text-listener
  [state {:keys [on-message on-open on-close on-error]}]
  (reify
    WebSocketCloseCodeReasonListener
    (onClose [_ ws code reason]
      (when on-close
        (on-close ws code reason))
      (reset! state nil))

    WebSocketListener
    (^{:tag void} onOpen [_ #^WebSocket socket]
      (reset! state socket)
      (when on-open
        (on-open socket)))

    (^{:tag void} onClose [_ #^WebSocket socket])

    (^{:tag void} onError [_ #^Throwable t]
      (reset! state nil)
      (when on-error
        (on-error @state t)))

    WebSocketTextListener
    (^{:tag void} onMessage [_ #^String s]
      (when on-message
        (on-message @state s)))

    WebSocketTextFragmentListener
    (^{:tag void} onFragment [_ #^HttpResponseBodyPart part])))

(defn upgrade-handler
  [callbacks]
  (let [b (WebSocketUpgradeHandler$Builder.)
        state (atom nil)]
    (.addWebSocketListener b (create-text-listener state callbacks))
    (.build b)))

(defn websocket
  ([url callbacks]
   (websocket url callbacks nil))
  ([url callbacks config]
   (let [client (if config
                  (DefaultAsyncHttpClient. config)
                  (DefaultAsyncHttpClient.))]
     (.. client
         (prepareGet url)
         (execute (upgrade-handler callbacks))
         (get)))))

(defn send-message [ws message]
  (.sendMessage ws message))