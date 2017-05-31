(ns quantum-device-manager.example
  (:require [clojure.pprint :refer [pprint]]
            [datomic.api :as d]
            [quantum-device-manager.core :as q]))

(def conn (d/connect "datomic:free://localhost:4334/quantum-device-manager"))

(q/ensure-schemas conn)

;; create our device, qubits, and gates

(def device-name "2-qubit-prototype")

;; If the device already exists from a prior run, let's delete it
(when-let [device (q/name->device (d/db conn) device-name)]
  @(d/transact conn [{:db/id #db/id[:db.part/user] :db/excise (:db/id device)}])
  (Thread/sleep 1000))

(q/create-device conn {:device/name device-name
                       :device/description "Shave and a haircut, two qubits!"})

;; so we can refer to it later
(def device (q/name->device (d/db conn) device-name))

(q/create-qubit conn {:qubit/device-id (:db/id device)
                      :qubit/position 0N
                      :qubit/resonance-GHz 20.0
                      :qubit/coherence-time-0-us 100.0
                      :qubit/coherence-time-1-us 200.0})

(def qubit-0 (q/device-position->qubit (d/db conn) device 0))

(q/create-qubit conn {:qubit/device-id (:db/id device)
                      :qubit/position 1N
                      :qubit/resonance-GHz 22.0
                      :qubit/coherence-time-0-us 102.0
                      :qubit/coherence-time-1-us 210.0})

(def qubit-1 (q/device-position->qubit (d/db conn) device 1))

;; create 2 gates for each qubit
(doseq [qid (mapv :db/id [qubit-0 qubit-1])
        [name amp phase width] [["+X" 1.0 1.02 100.0] ["-Y/2" 2.0 0.785 95.0]]]
  (q/create-gate conn {:gate/qubit-id qid
                       :gate/name name
                       :gate/amplitude-mV amp
                       :gate/phase-rad phase
                       :gate/width-ns width}))

;; Let's take a look at the "+X" gate of the 2nd qubit
(def gate (q/qubit-name->gate (d/db conn) qubit-1 "+X"))
(pprint gate)
;; Result:
;; {:db/id 17592186045515,
;;  :gate/qubit-id {:db/id 17592186045509},
;;  :gate/name "+X",
;;  :gate/amplitude-mV 1.0,
;;  :gate/composite-id 1325654162,
;;  :gate/width-ns 100.0,
;;  :gate/phase-rad 1.02}

;; We can get the qubit that this gate belongs to
(pprint (q/gate->qubit (d/db conn) gate))
;; Result:
;; {:db/id 17592186045509,
;;  :qubit/device-id {:db/id 17592186045505},
;;  :qubit/position 1,
;;  :qubit/resonance-GHz 22.0,
;;  :qubit/coherence-time-0-us 102.0,
;;  :qubit/coherence-time-1-us 210.0,
;;  :qubit/gates [{:db/id 17592186045515} {:db/id 17592186045517}],
;;  :qubit/composite-id -2034853687}

;; We can get the device that this gate, transitively, belongs to
(pprint (q/gate->device (d/db conn) gate))
;; Result:
;; {:db/id 17592186045505,
;;  :device/qubits [{:db/id 17592186045507} {:db/id 17592186045509}],
;;  :device/name "2-qubit-prototype",
;;  :device/description "Shave and a haircut, two qubits!"}

;; Let's look at all of our device's gates
(pprint (q/device->gates (d/db conn) device))
;; Result:
;; [{:db/id 17592186045511,
;;   :gate/qubit-id {:db/id 17592186045507},
;;   :gate/name "+X",
;;   :gate/amplitude-mV 1.0,
;;   :gate/composite-id -536191745,
;;   :gate/width-ns 100.0,
;;   :gate/phase-rad 1.02}
;;  {:db/id 17592186045513,
;;   :gate/qubit-id {:db/id 17592186045507},
;;   :gate/name "-Y/2",
;;   :gate/amplitude-mV 2.0,
;;   :gate/composite-id -545674543,
;;   :gate/width-ns 95.0,
;;   :gate/phase-rad 0.785}
;;  {:db/id 17592186045515,
;;   :gate/qubit-id {:db/id 17592186045509},
;;   :gate/name "+X",
;;   :gate/amplitude-mV 1.0,
;;   :gate/composite-id 1325654162,
;;   :gate/width-ns 100.0,
;;   :gate/phase-rad 1.02}
;;  {:db/id 17592186045517,
;;   :gate/qubit-id {:db/id 17592186045509},
;;   :gate/name "-Y/2",
;;   :gate/amplitude-mV 2.0,
;;   :gate/composite-id 736632269,
;;   :gate/width-ns 95.0,
;;   :gate/phase-rad 0.785}]

;; Let's say that we were able to measure the resonance of the qubit in the 0 position
;; to a few more
(def db-before-qubit-change (d/db conn))
(q/update-qubit conn (merge (q/device-position->qubit db-before-qubit-change device 0)
                            {:qubit/resonance-GHz 20.103}))
(def db-after-qubit-change (d/db conn))
;; Let's look at the new value of the qubit.
(pprint (q/device-position->qubit db-after-qubit-change device 0))
;; Result:
;; {:db/id 17592186045507,
;;  :qubit/device-id {:db/id 17592186045505},
;;  :qubit/position 0,
;;  :qubit/resonance-GHz 20.103,
;;  :qubit/coherence-time-0-us 100.0,
;;  :qubit/coherence-time-1-us 200.0,
;;  :qubit/gates [{:db/id 17592186045511} {:db/id 17592186045513}],
;;  :qubit/composite-id 1298572670}

;; We can still see what value the qubit had before the change.
(pprint (q/device-position->qubit db-before-qubit-change device 0))
;; Result:
;; {:db/id 17592186045507,
;;  :qubit/device-id {:db/id 17592186045505},
;;  :qubit/position 0,
;;  :qubit/resonance-GHz 20.0,
;;  :qubit/coherence-time-0-us 100.0,
;;  :qubit/coherence-time-1-us 200.0,
;;  :qubit/gates [{:db/id 17592186045511} {:db/id 17592186045513}],
;;  :qubit/composite-id 1298572670}

;; We are time travelers.
;; We can see what value the qubit had at any point in time by using as-of to obtain the
;; database value for any point in time.
(pprint (q/device-position->qubit (d/as-of (d/db conn) (.getTime (java.util.Date.)))
                                  device
                                  0))
;; Result:
;; {:db/id 17592186045507,
;;  :qubit/device-id {:db/id 17592186045505},
;;  :qubit/position 0,
;;  :qubit/resonance-GHz 20.103,
;;  :qubit/coherence-time-0-us 100.0,
;;  :qubit/coherence-time-1-us 200.0,
;;  :qubit/gates [{:db/id 17592186045511} {:db/id 17592186045513}],
;;  :qubit/composite-id 1298572670}
