## Overview

Distributed hash tables are a decentralized, distributed, key-value lookup
structure. This is a Java implementation of the Chord design, which uses
consistent hashing to partition data over a collection of processes in a network
and finger tables to efficiently search for keys, on average making log(n)
network requests per lookup. Note the stabilization algorithm is being
implemented.

See http://nms.csail.mit.edu/papers/chord.pdf for the original paper.
