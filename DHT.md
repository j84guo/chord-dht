*A Scalable Peer-to-peer lookup service.*
Notes on the original paper. http://nms.csail.mit.edu/papers/chord.pdf

**Peer-to-peer:**
Peer-to-peer systems are distributed systems without any central point of
control or hierarchical organization. Desirable characteristics of such systems
include scalable storage, availability, redundancy, load balance,
peer discovery, peer churn, anonymity, search and authentication.

**Chord:**
A fundamental requirement in peer-to-peer applications is locating a node which
stores a piece of data. Chord is a distributed lookup protocol which addresses
this need. The single primitive supported by Chord is to map a given hash onto a
node which is participating in the network. The protocol supports nodes
continuously joining and leaving. In the stable state, communication cost scales
logarithmically with the number of nodes.

**Related work:**
Although the Chord protocol only specifies a mapping from a hash to a node,
distributed key-value data storage can be achieved by hashing a key and storing
the value on the node responsible for that hash.

***DNS***
DNS provides a hostname to ip address lookup service.

***Napster***
Napster is a distributed data store using a central index, which presents a
single point of failure.

***Gnutella***
Gnutella is a distributed data store using a flooding search technique, which is
unscalable in a large network.

**Base Protocol**
Concurrent joins and failures are discussed in the next section.

***Overview***
Chord provides fast computation of a distributed hash function mapping keys to
nodes.

Chord permits scalability by avoiding each node knowing about every other node.
A node only maintains a small amount of routing information. Lookup is then
resolved by communicating with a small number of other nodes O(log(n) messages).

Nodes must update their routing information when a new node joins or leaves,
O(log^2(n) messages)

***Consistent Hashing***
The hash function assigns each node and key an m-bit identifier, using a
well known hash function such as SHA-256 and truncating the output.

A node's ip address and port are hashed while a piece of data has its key
hashed. The length m must be large enough to make the probability of collisions
negligible.

If identifiers are represented as points on a circle from 0 to 2^m-1, then the
successor of a key is the node equal to or immediately clockwise, while the
predecessor is counter-clockwise. Thus, each nodes owns a partition of the total
key space. These partitions are resized as nodes join and leave.

With high probability, all nodes receive roughly the same amount of keys.
When an nth node joins or leaves the network, only an O(1/n) fraction of the
existing keys are moved to a different location. A simple explanation for this
conclusion is that, given random nodes and keys, the resulting hash values
are unlikely to be very close together, given a good hash function.

***Scalable Key Location***
A small amount of routing information suffices to implement correct lookup. Each
node only needs to know its immediate successor. Queries for a key can then be
routed around the circle to the responsible bucket.

This simplified technique is inefficient, however, since it may require
traversing all the nodes on the circle. A more efficient technique is to
maintain a routing table with m entries at each node, called the finger table.

Each finger is a shortcut that can be taken to speed lookup. It is the first
node greater than or equal to n.node + 2^(i-1) (mod 2^m), where 1 <= i <= m. We
denote finger i as successor(n + 2^(i-1) (mod 2^m)) or n.finger[i].node.

Finger correctness is required for efficient lookup, but lookup correctness is
still maintained if some fingers are wrong.

***Notation***
finger[k].start -> n + 2^(m-1) (mod 2^m), where 1 <= k <= m // point at or past which we look for the successor
finger[k].interval -> [finger[k].start, finger[k+1].start) // next finger's interval, used to determine whether the finger precedes a certain key
finger[k].node -> first node >= finger[k].start
finger[k].successor -> finger[1].node is the immediate clockwise successor
finger[k].predecessor -> previous node on the identifier circle

***Overlay Network Invariants***
In a dynamic environment, nodes can join and leave at any time. To preserve the
correctness of lookups under churn, two invariants need to be maintained.

1. Each node's successor is correctly maintained.
2. For every key k, the node successor(k) is responsible for k.

In order for lookups to be fast, it is desirable for fingers to be correct.

***Discovery Pseudocode***
// ask node n to find id's successor
n.find_successor(id)
  np = find_predecessor(id)
  return np.successor

// ask node n to find id's predecessor
// a predecessor must be less than the key, although a successor may be greater than or equal
// if n itself is passed in (in a one node network) it is returned
// if n-1 is passed in (in a 2 node network) then n is returned
n.find_predecessor(id)
  np = n
  while id not in (np, np.successor]
    np = np.closest_preceding_finger(id)
  return np

// return closest finger preceding id
// go backwards so that we can stop at the first node falling into the range
// by definition, the finger must succeed n, but it also must precede id
// if n itself is passed in, all iterations will fall through and n will be returned
n.closest_preceding_finger(id)
  for i=m down to 1
    if finger[i].node in (n, id)
      return finger[i].node
  return n

***Node Joins: Overview***
To simply join and leave operations, each also maintains a pointer to its
predecessor, which consists of the chord identifier, ip address and port of the
other node.

To preserve the invariants, Chord must perform the following steps when a node,
n, joins the network.

1. Initialize the predecessor and fingers of n.
2. Update the predecessor and fingers of existing nodes to reflect the addition
   of n
3. Transfer appropriate keys to new node

A new node learns the ip address and port of an existing node np by some
external mechanism. Usually, there are a pool of well known bootstrap nodes. A
new node n uses np to initialize its state and join the network, as follows.

***Node Joins: Pseudocode***
// node n joins network
// np is an arbitrary node already in the network
// either n is the first node in the network and we point all fingers to ourself
// or there is >= 1 bootstraps and we ask it for initial state
n.join(np)
  if np exists
    init_finger_table(np)
    update_others()
    // application : move appropriate keys from successor to n
  else
    for i=1 to m
      finger[i].node = n
    predecessor = n

// initialize finger table of local node
// np is an arbitrary node already in the network
// successor = finger[1].node
// i=1 to m-1 updates nodes 2 to m
// the next largest finger either uses the same node as the previous
// or we need to query the bootstrap node for the finger's successor
n.init_finger_table(np)

  finger[1].node = np.find_successor(finger[1].start)
  predecessor = successor.predecessor
  successor.predecessor = n

  for i=1 to m-1
    if finger[i+1].start in [n, finger[i].node)
      finger[i+1].node = finger[i].node
    else
      finger[i+1].node = np.find_successor(finger[i+1].start)

// update all nodes whose finger table should refer to n
// since find_predecessor returns the node less than id, we add 1 to n - 2^(i-1)
n.update_others()
  for i=1 to m
    p = find_predecessor(n - 2^(i-1) + 1)
    p.update_finger_table(n, i)

// if s is finger i of n, update n's finger table with s
// updating finger i is conditional on the id falling in the range [n, finger[i].node)
// the [ should be redundant now since find_predecessor returns a node less than id
// meaning n (in this function the found predecessor) will be less than the new node
n.update_finger_table(s, i)
  if s in [n, finger[i].node)

***1. Initializing predecessor and fingers***
Node n learns initializes its predecessor and fingers by asking a known node np
to look them up (init_finger_table). A naive approach of querying find_successor
for each finger would give a runtime of O(log n). To reduce, we first check if
finger i's node also works for finger i + 1. Apparently it can be shown that the
expected number of finger entries which need to be looked up is O(log n). Note
the successor is updated to reflect n as its predecessor.

***2. Update predecessor and fingers to reflect addition of n***
Node n needs to be reflected in the finger tables of some existing nodes.
Preceding nodes which may need a finger i update must be at least 2^(i-1) before
the new node. Therefore we find the predecessor of n - 2^(i-1) + 1 (mod m). At
such a node, we check that the new node is replacement for the existing finger i
and update the finger if true. In this case, we continue walking backwards while
consecutive nodes need n as their finger for the current value of i.

***3. Transfer appropriate keys to new node***
At the application layer, keys and associated data/work may need to be moved
from the new node's successor to the new node.

**Concurrent Joins and (presumably non-concurrent) Failures**
In practice, peer-to-peer systems must deal with concurrent joins and nodes who
fail or leave voluntarily. A modification to the basic chord algorithm handles
these situations.

***Stabilization***
The previously described join algorithm aggressively maintains the finger tables
of all nodes as the network evolves, however this is hard to maintain in the
face of frequent joins and failures in a large network. A periodic
stabilization process runs at each node in order to main the correctness of
successor pointers, and these successors are then used to adjust fingers. Recall
that the successor's guarantee correctness while fingers improve lookup
performance.

Lookups which occur over an area of the network that has been affected by
concurrent joins can fall into three cases:

1. Fingers are reasonably current, so the lookup succeeds in O(log n) steps.
2. Successor pointers are correct, but fingers are not, so lookups succeeds, but
   more slowly.
3. Successors are incorrect or keys have not migrated properly, so the lookup
   fails. Application software may retry the lookup after a short interval, when
   stabilization will likely have fixed the nodes.

Supposedly, stabilization guarantees that new nodes will eventually be reachable
from any other node. Note that the extreme cases of the network splitting into
multiple disjoint sets cannot be fixed by stabilization, but requires external
sampling and repair mechanisms.

***Stabilization Pseudocode***
// only sets n's successor
n.join(np)
  predecessor = null
  successor = np.find_successor(n)

// periodically verify n's immediate successor
// and notify the successor of n's presence
n.stabilize()
  x = successor.predecessor
  if x in (n, successor)
    successor = x
  // if the successor's predecessor is ahead of n, we notify it of n's existence as its predecessor
  // else the successor's predecessor is out of data, we notify n's successor of n's existence as its predecessor
  successor.notify(n)

// np thinks it might be our predecessor
n.notify(np)
  if predecessor is null or np in (predecessor, n)
    predecessor = np

// periodically refresh finger table entries
n.fix_fingers()
  i = random index > 1 into finger[] // why random and why > 1?
  finger[i].node = find_successor(finger[i].start)



**Simulation**
