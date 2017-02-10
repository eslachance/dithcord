(ns dithcord.storage
  (:gen-class)
  (:require [datascript.core :as d]
            [clojure.string :as str]))

(def schema
  {:guild/id {:db/unique :db.unique/identity}
   :channel/id {:db/unique :db.unique/identity}
   :message/id {:db/unique :db.unique/identity}
   :role/id {:db/unique :db.unique/identity}
   :emoji/id {:db/unique :db.unique/identity}
   :user/id {:db/unique :db.unique/identity}
   :user {:db/type :db.type/ref}
   :guild {:db/type :db.type/ref}
   :channel {:db/type :db.type/ref}
   })

(def conn (d/create-conn schema))

(defn insert! [data, type]
  (let [inserts (into {:db/id -1}
                      (filter some? (map (fn [[k v]] (if (some? v) [(keyword type (name k)) v])) data)))
        result (d/transact! conn [inserts])]
    ;(get (:tempids result) -1)
    result
    ))

(defn get-item [type id]
  (seq (d/entity @conn [(keyword (str type "/id")) id])))

(defn underscore->dash [v]
  (str/replace v #"_" "-"))

(defn singular [v]
  (str/replace v #"s$" ""))

(defn namespace-map [ns coll]
  (->> coll
       (map (fn [[k v]]
              [(keyword (underscore->dash ns)
                        (underscore->dash (name k)))
               v]))
       (remove (comp nil? second))
       (into {})))

(defn children? [coll]
  (and (sequential? coll) (every? map? coll)))

(defn rel? [x]
  (and (map? x)
       (= (count x) 1)
       (contains? x :id)))

(defn entity? [x]
  (and (map? x)
       (> (count x) 1)
       (contains? x :id)))

(defn inlinable? [x]
  (and (map? x)
       (not (contains? x :id))))

(defn flatten-fact [fact]
  (loop [result {}
         fact fact
         acc []]
    (if-not (empty? fact)
      (let [[k v] (first fact)]
        (cond
          (rel? v)
          (recur (assoc result (keyword (name k)) [(keyword (name k) "id") (:id v)])
                 (rest fact)
                 acc)
          (entity? v)
          (recur (assoc result (keyword (name k)) [(keyword (name k) "id") (:id v)])
                 (rest fact)
                 (conj acc (namespace-map (name k) v)))
          (inlinable? v)
          (recur (merge result (namespace-map (name k) v))
                 (rest fact)
                 acc)
          :else
          (recur (assoc result k v)
                 (rest fact)
                 acc)))
      [result acc])))

(defn flatten-map
  ([children]
   (flatten-map children nil))
  ([children process-fns]
   (flatten-map children process-fns nil))
  ([children process-fns template]
   (apply concat
          (for [[k v] children
                row v]
            (let [entity (singular (name k))
                  process-fn (get process-fns (keyword (underscore->dash entity)) identity)
                  [fact rels] (->> (remove (comp children? second) row)
                                   (into {})
                                   (process-fn)
                                   (namespace-map entity)
                                   (flatten-fact))
                  children (filter (comp children? second) row)]
              (concat
                rels
                [(merge template fact)]
                (flatten-map children process-fns {(keyword entity) [(keyword entity "id") (:id row)]})))))))

(defn sort-facts [facts]
  (sort-by #(or (get (clojure.walk/stringify-keys %) "id") "z") facts))

(defn massage [session msg type]
  (let [packet (msg :d)
        map-name (-> (str/split (msg :t) #"_") first str/lower-case)]
    (case type
      "PRESENCE_UPDATE" (let [newpacket {:user-id (-> packet :user :id)
                                         :guild-id (packet :guild_id)
                                         :nick (packet :nick)
                                         :author {:id (-> packet :user :id)
                                                  :status (packet :status)
                                                  :game (packet :game)}}]
                          (d/transact! conn (sort-facts (flatten-map {:presence [newpacket]}))))
      "default" (let [filters {:permission-overwrite
                               (fn [row]
                                 (let [ref-key (keyword (str (:type row) "-id"))
                                       ref-id (:id row)]
                                   (-> row
                                       (dissoc :id)
                                       (assoc ref-key ref-id))))}]
                  (d/transact! conn (sort-facts (flatten-map {(keyword map-name) [packet]} filters)))))))

(defn -main []
  ;blah
  )