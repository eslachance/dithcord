<div align="center"><p>&nbsp;</p><img src="http://i.imgur.com/rt1yACk.png"><p>&nbsp;</p></div>

Dithcord is a **WORK IN PROGRESS** library for Discord Bots. And by WIP I mean, it's fairly useless right now and is in heavy development. In other words, don't download it.

> If you're wondering about the name, it's because Dithcord is built on Clojure, which is a *lisp*. Yes, I think it's a clever pun. No, I don't think it's ableist to joke about lisps. No more than the fact that the word itself is pronounced `lithp` if you have it.

## Installation

[Leiningen](https://github.com/technomancy/leiningen) dependency information:

> This is a placeholder. I'm not on clojars.

```clj
 [dithcord "0.0.8"]
```

## Example

> Ping/Pong example (pong doesn't work yet, yay)

```clj
(ns dtbot.core
  (:require [dithcord.core :as dithcord]))

(defn handle-message [session message]
  (if (= (message :content) "!ping")
    (dithcord/send-message session "pong!" (message :channel_id))))

(defn on-ready [session]
  (prn "Dithcord Tetht is ready to serve!"))

(def handlers {:MESSAGE_CREATE [handle-message]
               :READY          [on-ready]})

(def session (dithcord/connect
                 {:token "YOUR TOKEN HERE"
                  :handlers handlers
                  }))
```

## Documentation

> To be done

## Credits

- @Jagrosh#4824 : For coming up with the name "Dithcord".
- @Trippehh#1782 for great work on the logo
- My trusted allies on Discord for pushing me to succeed
