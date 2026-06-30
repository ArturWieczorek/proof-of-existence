//// The proof registry as an on-chain SORTED SET of notarized hashes.
////
//// Metadata-only notarization (Ch 02) cannot stop the same hash being recorded twice. If you want
//// "a document can be notarized at most once", you need on-chain state. We use the same sorted
//// linked-list trick as a nonce list: each hash is a node, nodes are chained in ascending order,
//// and inserting a new hash proves it is NOT already present (it must fit strictly between two
//// existing nodes). Membership = a node with that key exists.

use aiken/primitive/bytearray

/// Root sentinel key (empty, sorts below everything; not a real hash).
pub const root_key: ByteArray = ""

/// Tail sentinel: 33 bytes of 0xff, above any 32-byte hash.
pub const max_key: ByteArray =
  #"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

/// Each registry node UTxO carries a marker token with this (empty) asset name.
pub const node_token_name: ByteArray = ""

/// One node: a notarized hash `key` linking to the next hash `next`.
pub type RegistryNode {
  key: ByteArray,
  next: ByteArray,
}

/// True when `key` falls strictly inside this node's gap: node.key < key < node.next. The unique
/// such node is the "anchor" a new key must be inserted after. A duplicate key is NOT strictly
/// between any gap, so it can never be inserted - that is the uniqueness guarantee.
pub fn covers(node: RegistryNode, key: ByteArray) -> Bool {
  bytearray.compare(node.key, key) == Less && bytearray.compare(key, node.next) == Less
}

/// Inserting `key` splits the anchor:
///   before: anchor(key=A, next=B)
///   after:  anchor(key=A, next=key)  ++  inserted(key=key, next=B)
pub fn valid_insertion(
  anchor_old: RegistryNode,
  anchor_new: RegistryNode,
  inserted: RegistryNode,
  key: ByteArray,
) -> Bool {
  and {
    covers(anchor_old, key),
    anchor_new.key == anchor_old.key,
    anchor_new.next == key,
    inserted.key == key,
    inserted.next == anchor_old.next,
  }
}

// ----------------------------------------------------------------------------
// Pure tests
// ----------------------------------------------------------------------------

const hash_a: ByteArray =
  #"0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a"

const hash_m: ByteArray =
  #"5050505050505050505050505050505050505050505050505050505050505050"

const hash_z: ByteArray =
  #"f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0"

test root_covers_any_hash() {
  let root = RegistryNode { key: root_key, next: max_key }
  covers(root, hash_a) && covers(root, hash_z)
}

test covers_is_strict() {
  let node = RegistryNode { key: hash_a, next: hash_z }
  covers(node, hash_m) && !covers(node, hash_a) && !covers(node, hash_z)
}

test valid_insertion_splits_anchor() {
  let anchor_old = RegistryNode { key: hash_a, next: hash_z }
  let anchor_new = RegistryNode { key: hash_a, next: hash_m }
  let inserted = RegistryNode { key: hash_m, next: hash_z }
  valid_insertion(anchor_old, anchor_new, inserted, hash_m)
}

test duplicate_hash_is_rejected() {
  // trying to "insert" a hash equal to the anchor's key is not strictly between -> rejected.
  let anchor_old = RegistryNode { key: hash_a, next: hash_z }
  let anchor_new = RegistryNode { key: hash_a, next: hash_a }
  let inserted = RegistryNode { key: hash_a, next: hash_z }
  !valid_insertion(anchor_old, anchor_new, inserted, hash_a)
}
