(ns dithcord.storage
  (:gen-class)
  (:require [datascript.core :as d]))

(def schema
  {:config/key {:db/unique :db.unique/identity}
   :guild/id {:db/unique :db.unique/identity}
   :channel/id {:db/unique :db.unique/identity}
   :message/id {:db/unique :db.unique/identity}
   :message/author {:db/type :db.type/ref}
   :role/id {:db/unique :db.unique/identity}
   :emoji/id {:db/unique :db.unique/identity}
   :presence/id {:db/unique :db.unique/identity}
   :member/id {:db/unique :db.unique/identity}
   :user/id {:db/unique :db.unique/identity}
   })

(def conn (d/create-conn schema))

(defn get-conf [key]
     (:config/value (d/entity @conn [:config/key key])))

(defn insert! [data, type]
          (let [inserts (into {:db/id -1}
                              (filter some? (map (fn [[k v]] (if (some? v) [(keyword type (name k)) v])) data)))
                result (d/transact! conn [inserts])]
            (get (:tempids result) -1)))

(defn get-item [type id]
  (seq (d/entity @conn [(keyword (str type "/id")) id])))

(defn set-conf [key value]
  (d/transact!
    conn
    [{:config/key key
      :config/value value}]))

(defn set-multi [type, datum]

  ;(doall )
  )

(defn GUILD_CREATE [packet]
  (let [data (packet :d)
        guild (dissoc data :emojis :channels :roles :presences :members)]
    (insert! guild "guild")
    (set-multi "emoji" (data :emojis))
    (set-multi "channel" (data :channels))
    (set-multi "roles" (data :roles))
    (set-multi "presence" (data :presence))
    (set-multi "member" (data :members))
    ))
;data (packet :d)

(defn MESSAGE_CREATE [data]
  (let [message (dissoc data :mentions :attachments :author :mention_roles :embeds)
        user-ref (insert! (message :author) "user")]
    (prn (str "Inserting reference " user-ref " as an author to the message " (message :id)))
    (insert! (assoc message :author user-ref) "message")
    ))


(defn -main []
  ;blah
  )