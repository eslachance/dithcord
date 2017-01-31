(ns dithcord.client
  (:require [dithcord.core :as d]
            [dithcord.storage :as ds]
            [clojure.edn :as edn]))

(def config (edn/read-string (slurp "config.edn")))

(defn message [session message]
  (let [prefix (config :prefix)
        ownerid (config :ownerid)
        content (message :content)]
    (ds/MESSAGE_CREATE message)
    (prn (str "Message Received by client: " content))
    (if (= content (str prefix "ping"))
      (d/send-message session "pong!" (message :channel_id)))
    (if (= content (str prefix "info"))
      (d/send-message session "This bot is built with Dithcord! <https://github.com/eslachance/dithcord/>" (message :channel_id)))
    ))

(defn guildMemberAdd [session member]
  (do
    ;(prn member)
    (d/send-message session (str "New Member has joined: " (get-in member [:user :username]) "(" (get-in member [:user :id]) ")!") "271083137994326016")
    ))

(defn guildMemberRemove [session member]
  (do
    ;(prn member)
    (d/send-message session (str "Member has left: " (get-in member [:user :username]) "(" (get-in member [:user :id]) ")!") "271083137994326016")
    ))

(defn ready [session]
  (prn "Dithcord Tetht is ready to serve!"))

(def handlers {:MESSAGE_CREATE [message]
               :GUILD_MEMBER_ADD [guildMemberAdd]
               :GUILD_MEMBER_REMOVE [guildMemberRemove]})

(def session (d/connect
               {:token (config :token)
                :handlers handlers
                }))

#_(defn -main
    []
    (dithcord/connect
      {:token (config :token)
       :handlers handlers
       }))
