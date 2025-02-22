(ns mire.server
  (:require [clojure.java.io :as io]
            [server.socket :as socket]
            [mire.player :as player]
            [mire.commands :as commands]
            [mire.rooms :as rooms]))

(defn- cleanup []
  "Drop all inventory and remove player from room and player list."
  (dosync
   (doseq [item @player/*inventory*]
     (commands/discard item))
   (commute player/streams dissoc player/*name*)
   (commute (:inhabitants @player/*current-room*)
            disj player/*name*)))

(defn- get-unique-player-name [name]
  (flush)
  (if (@player/streams name)
    (do (print "That name is in use; try again: ")
        (flush)
        (recur (read-line)))
     name))

(defn- get-strength [strength]
  (if (>(+ (Integer/parseInt strength) player/*intelligence* player/*perception*) 10)
    (do (print "\nSum of stats > 10. Input a lower number: ")
      (flush)
      (recur (read-line)))
    strength)) ;;ueeee

(defn- get-intelligence [intelligence]
  (if (>(+ (Integer/parseInt intelligence) player/*strength* player/*perception*) 10)
    (do (print "\nSum of stats > 10. Input a lower number or 0: ")
      (flush)
      (recur (read-line)))
    intelligence)) ;;ueeeee

(defn- get-perception [perception]
  (if (>(+ (Integer/parseInt perception) player/*intelligence* player/*strength*) 10)
    (do (print "\nSum of stats > 10. Input a lower number or 0: ")
      (flush)
      (recur (read-line)))
    perception)) ;;ueeeeeeee

(defn- filter-crap [string]
  (if (> (count string) 20)
    (subs string 20)
    string
  ))
(defn- mire-handle-client [in out]
  (binding [*in* (io/reader in)
            *out* (io/writer out)
            *err* (io/writer System/err)
            player/*strength* 0
            player/*intelligence* 0
            player/*perception* 0]

    ;; We have to nest this in another binding call instead of using
    ;; the one above so *in* and *out* will be bound to the socket
    (print "\nWhat is your name? (Press Enter, then input your name and press Enter)")
    (flush)
    (read-line) ;Ебанный костыль
    (binding [player/*name* (get-unique-player-name (read-line))
              player/*current-room* (ref (@rooms/rooms :start))
              player/*inventory* (ref #{})]
      (dosync
       (commute (:inhabitants @player/*current-room*) conj player/*name*)
       (commute player/streams assoc player/*name* *out*))

      (print "Write the description about you: ") (flush)
      (binding [player/*description* (read-line)])
      (print "\nWhat is your strength? Input number from 0 to 10: ") (flush)
      (binding [player/*strength* (Integer/parseInt (try (get-strength (read-line))
                                                      (catch Exception e
                                                      (.printStackTrace e (new java.io.PrintWriter *err*))
                                                      "Input can only be an integer in range [1,10]")))] ;;ueeee
        (print "\nWhat is your intelligence? Input number from 0 to 10: ") (flush)
        (binding [player/*intelligence* (Integer/parseInt (try (get-intelligence (read-line))
                                                          (catch Exception e
                                                          (.printStackTrace e (new java.io.PrintWriter *err*))
                                                          "Input can only be an integer in range [1,10]")))] ;;ueeee
          (print "\nWhat is your perception? Input number from 0 to 10: ") (flush)
          (binding [player/*perception* (Integer/parseInt (try (get-perception (read-line))
                                                          (catch Exception e
                                                          (.printStackTrace e (new java.io.PrintWriter *err*))
                                                          "Input can only be an integer in range [1,10]")))] ;;ueeeeeee

      (println (commands/look)) (print player/prompt) (flush)

      (try (loop [input (read-line)]
             (when input
               (println (commands/execute input))
               (.flush *err*)
               (print player/prompt) (flush)
               (recur (read-line))))
           (finally (cleanup)))))))))

(defn -main
  ([port dir]
     (rooms/add-rooms dir)
     (defonce server (socket/create-server (Integer. port) mire-handle-client))
     (println "Launching Mire server on port" port))
  ([port] (-main port "resources/rooms"))
  ([] (-main 3333)))
