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
    :db/ident :gate/width
    :db/doc "Time width of the gate in nanoseconds"
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :gate/phase
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

(def conn (d/connect "datomic:free://localhost:4334/quantum-device-manager"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Device Interface
(defn create-device [conn device]
  @(d/transact conn [(merge device {:db/id #db/id[:db.part/user]})]))

(defn device-name->device [db device-name]
  (first (mapv first (d/q '[:find (pull ?e [*])
                            :in $ ?name
                            :where [?e :device/name ?name]]
                          db
                          device-name))))

(defn qubit-id->device [db qubit-id]
  (first (mapv first (d/q '[:find (pull ?e [*])
                            :in $ ?qubit-id
                            :where [?e :device/qubits ?qubit-id]]
                          db
                          qubit-id))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Qubit Interface
(defn create-qubit [conn qubit]
  (let [device-id (:qubit/device-id qubit)
        qubit-id (d/tempid :db.part/user)
        id (massage-id [device-id (:qubit/position qubit)])
        entity (merge qubit {:qubit/composite-id (bigint id)
                             :db/id qubit-id})]
    @(d/transact conn [entity
                       {:db/id device-id
                        :device/qubits qubit-id}])))

(defn device-position->qubit [db device-id position]
  (first (mapv first (d/q '[:find (pull ?e [*])
                            :in $ ?id
                            :where [?e :qubit/composite-id ?id]]
                          db
                          (massage-id [device-id position])))))

(defn device-id->qubits [db device-id]
  (:device/qubits (d/entity db device-id)))

(defn gate-id->qubit [db gate-id]
  (first (mapv first (d/q '[:find (pull ?e [*])
                            :in $ ?gate-id
                            :where [e? :qubit/gates gate-id]]
                          db
                          gate-id))))

(defn update-qubit [conn qubit]
  @(d/transact conn [qubit]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Gate Interface
(defn create-gate [conn gate]
  (let [id (massage-id [(:gate/qubit-id gate) (:gate/name gate)])
        gate-id (d/tempid :db.part/user)
        entity (merge gate {:gate/composite-id (bigint id)
                            :db/id gate-id})]
    @(d/transact conn [entity
                       {:db/id (:gate/qubit-id gate)
                        :qubit/gates gate-id}])))

(defn qubit-id->gates [db qubit-id]
  (:qubit/gates (d/entity db qubit-id)))

(defn update-gate [conn gate]
  @(d/transact conn [gate]))
