(ns quantum-device-manager.core
  (:require [datomic.api :as d]))

(def device-schema
  [{:db/id #db/id[:db.part/db]
    :db/ident :device/name
    :db/doc "The name of the device e.g. 7-qubit-prototype"
    :db/valueType :db.type/string
    :db/unique :db.unique/value
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :device/description
    :db/doc "A short description of the device"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :device/qubits
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}])

(def qubit-schema
  [{:db/id #db/id[:db.part/db]
    :db/ident :qubit/device-id
    :db/doc "The id of the device where this qubit is located."
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :qubit/position
    :db/doc "This qubit's position in the device (0,1,2,3...) aka qubit id"
    :db/valueType :db.type/bigint
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :qubit/resonance-GHz
    :db/doc "Qubit's resonant frequency"
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :qubit/coherence-time-0-us
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :qubit/coherence-time-1-us
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :qubit/gates
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :qubit/composite-id
    :db/doc "This should be (hash [<device-id> <position>])"
    :db/valueType :db.type/bigint
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/value
    :db.install/_attribute :db.part/db}])

(def gate-schema
  [{:db/id #db/id[:db.part/db]
    :db/ident :gate/qubit-id
    :db/doc "Id of the qubit that this gate belongs to"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :gate/name
    :db/doc "Name of the gate e.g. +X, -Y/2, etc."
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :gate/amplitude-mV
    :db/doc "Amplitude of the gate in mV"
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :gate/width-ns
    :db/doc "Time width of the gate in nanoseconds"
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :gate/phase-rad
    :db/doc "Phase of the gate in radians"
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :gate/composite-id
    :db/doc "This should be (hash [<qubit-id> <name>])"
    :db/valueType :db.type/bigint
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/value
    :db.install/_attribute :db.part/db}])

(defn ensure-schemas 
  "Ensure the schemas are loaded in the database"
  [conn]
  @(d/transact conn (concat device-schema qubit-schema gate-schema)))

(defn massage-id [id]
  (if (integer? id)
    id
    (hash id)))

(defn lenient-id [id-or-entity]
  (or (:db/id id-or-entity) id-or-entity))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Device Interface
(defn create-device [conn device]
  @(d/transact conn [(merge {:db/id #db/id[:db.part/user]} device)]))

(defn name->device [db device-name]
  (first (mapv first (d/q '[:find (pull ?e [*])
                            :in $ ?name
                            :where [?e :device/name ?name]]
                          db
                          device-name))))

(defn qubit->device [db qubit]
  (first (mapv first (d/q '[:find (pull ?e [*])
                              :in $ ?qubit-id
                              :where [?e :device/qubits ?qubit-id]]
                            db
                            (lenient-id qubit)))))

(defn gate->device [db gate]
  (first (mapv first (d/q '[:find (pull ?device [*])
                            :in $ ?gate-id
                            :where
                            [?qubit :qubit/gates ?gate-id]
                            [?device :device/qubits ?qubit]]
                          db
                          (lenient-id gate)))))

(defn update-device [conn device]
  (assert (:db/id device) "The device must have a :db/id field.")
  @(d/transact conn [device]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Qubit Interface
(defn create-qubit [conn qubit]
  (let [device-id (:qubit/device-id qubit)
        qubit-id (d/tempid :db.part/user)
        id (massage-id [device-id (:qubit/position qubit)])
        entity (merge {:qubit/composite-id (bigint id)
                       :db/id qubit-id}
                      qubit)]
    @(d/transact conn [entity
                       {:db/id device-id
                        :device/qubits qubit-id}])))

(defn device-position->qubit [db device position]
  (d/touch (d/entity db
                     [:qubit/composite-id (massage-id [(lenient-id device)
                                                       position])])))

(defn device->qubits [db device]
  (:device/qubits (d/entity db (lenient-id device))))

(defn gate->qubit [db gate]
  (first (mapv first (d/q '[:find (pull ?e [*])
                            :in $ ?gate-id
                            :where [?e :qubit/gates ?gate-id]]
                          db
                          (lenient-id gate)))))

(defn update-qubit [conn qubit]
  (assert (:db/id qubit) "The qubit must have a :db/id field.")
  @(d/transact conn [qubit]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Gate Interface
(defn create-gate [conn gate]
  (let [id (massage-id [(:gate/qubit-id gate) (:gate/name gate)])
        gate-id (d/tempid :db.part/user)
        entity (merge {:gate/composite-id (bigint id)
                       :db/id gate-id}
                      gate)]
    @(d/transact conn [entity
                       {:db/id (:gate/qubit-id gate)
                        :qubit/gates gate-id}])))

(defn qubit->gates [db qubit]
  (:qubit/gates (d/entity db (lenient-id qubit))))

(defn device->gates [db device]
  (mapv first (d/q '[:find (pull ?gate [*])
                     :in $ ?device-id
                     :where
                     [?qubit :qubit/device-id ?device-id]
                     [?gate :gate/qubit-id ?qubit]]
                   db
                   (lenient-id device))))

(defn qubit-name->gate [db qubit name]
  (d/touch (d/entity db
                     [:gate/composite-id (massage-id [(lenient-id qubit)
                                                      name])])))

(defn update-gate [conn gate]
  (assert (:db/id gate) "The gate must have a :db/id field.")
  @(d/transact conn [gate]))
