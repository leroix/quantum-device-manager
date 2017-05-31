# quantum-device-manager
A shave and a haircut, two qubits!

## Rationale
I chose Datomic from which the use of Clojure followed naturally with queries being simply data structures. One reason I chose Datomic is the requirement that information stored in the database be versioned. Datomic provides this for free since the database is treated as a value. Viewing the past state of an entity is as simple as passing a prior database value as an argument. Prior database values can be retrieved using Datomic's "as-of" functionality. Also, Datomic's datalog query syntax made the code clear and concise--approximately 200 lines including the schema definitions!

For a production system, I see two primary drawbacks. Datomic is licensed software, and it's rather expensive. Also, there weren't any performance requirements in the problem description, but I would want to make sure that the queries are performant. Also, all Datomic transactions are processed by a single transactor. This could be a bottleneck in certain situations.

I also wanted to speak a bit about hiding or encapsulating vs. extending the Datomic API. I found the datalog query api to be extremely powerful. I chose to simply extend the Datomic API (rather than hide) with some simple functions for creating objects (entities) and traversing the hierarchy (e.g. gate->qubit, etc.) that take a db value as input. I felt it would be really beneficial for the user to still have access to the Datomic API. In fact, this was what helped the versioning shake out for free. A potential danger would be if the user tried to create transactions without going through the provided creation functions.

Finally, I chose not to provide functions for deleting entities. Datomic's philosophy is that deletion is seldom required and often a bad idea. It does provide [excision](http://docs.datomic.com/excision.html) in the rare cases where deletion must be done. Since the user is already using the Datomic api, I figured I would just let them use Datomic's excision functionality if they truly needed to delete something. I think that has the benefit of helping make sure they know what they're doing before they delete something.

## Usage examples
See https://github.com/leroix/quantum-device-manager/blob/master/src/quantum_device_manager/example.clj

## How to play
1. Download and run the leinengen [install script](https://leiningen.org/#install)
2. Start up datomic `docker run -d -p 4334-4336:4334-4336 --name datomic-free thosmos/datomic-free`
3. Run `lein repl` in the command-line
4. Type `(require '[quantum-device-manager.core :as q] '[datomic.api :as d])` in the repl
5. Follow along with https://github.com/leroix/quantum-device-manager/blob/master/src/quantum_device_manager/example.clj

## License
See LICENSE file.

Copyright © 2017 Justin Ratner

Distributed under the MIT License
