# Dithcord

Dithcord is a **WORK IN PROGRESS** library for Discord Bots. And by WIP I mean, it's fairly useless right now and is in heavy development. In other words, don't download it.

> If you're wondering about the name, it's because Dithcord is built on Clojure, which is a *lisp*. Yes, I think it's a clever pun. No, I don't think it's ableist to joke about lisps. No more than the fact that the word itself is pronounced `lithp` if you have it. Thanks for the name, Jagrosh!

## Installation

[Leiningen](https://github.com/technomancy/leiningen) dependency information:

> This is a placeholder. I'm not on clojars.

```clj
 [dithcord "0.2.1"]
```

## Example

> Ping/Pong example (pong doesn't work yet, yay)

```clj
(ns dtbot.core
  (:require [dithcord.core :as dithcord]))

(defn handle-message [message session]
  (if (= (get message :content) "ping")
    (do
      (prn "Ping Command Called")
      (let [response (dithcord/send-message session "pong!" (get message :channel_id))]
        (prn (response :done))
        (prn (response :error)))
      )
  ))

(defn on-ready [session]
  (prn "Ready to Serve!"))

(def handlers {:MESSAGE_CREATE handle-message
               :READY on-ready})

(dithcord/connect "your-bot-token" handlers)
```

## Documentation

> To be done

## License

Copyright © 2016 Évelyne Lachance

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
