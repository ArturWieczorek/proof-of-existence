//// Chapter 05 (optional) - the proof registry spending validator.
////
//// Guards the on-chain sorted set of notarized hashes. To insert a new hash, the transaction must
//// split the covering "anchor" node into [updated anchor] + [new node], proving the hash was not
//// already present (uniqueness). Structural rules live in lib/poe/registry.ak; this wires them to
//// the transaction via the continuing-output pattern.
////
//// NOTE: wiring this into the off-chain notary (build the insert transaction in Java) is left as an
//// integration step; this chapter delivers and verifies the on-chain logic with `aiken check`.

use aiken/collection/list
use cardano/address
use cardano/assets.{PolicyId}
use cardano/transaction.{
  InlineDatum, Input, Output, OutputReference, Transaction, placeholder,
}
use poe/registry.{RegistryNode, node_token_name, valid_insertion}

pub type RegistryRedeemer {
  InsertHash { key: ByteArray }
}

validator proof_registry(node_policy: PolicyId) {
  spend(
    datum: Option<RegistryNode>,
    redeemer: RegistryRedeemer,
    own_ref: OutputReference,
    self: Transaction,
  ) {
    expect Some(old) = datum
    expect Some(own_input) =
      list.find(self.inputs, fn(i) { i.output_reference == own_ref })
    let own_address = own_input.output.address
    let continuing =
      list.filter(self.outputs, fn(o) { o.address == own_address })

    // RegistryRedeemer has a single constructor, so destructure it with `let`.
    let InsertHash { key } = redeemer
    expect [o1, o2] = continuing
    expect is_node(o1, node_policy)
    expect is_node(o2, node_policy)
    expect InlineDatum(d1) = o1.datum
    expect n1: RegistryNode = d1
    expect InlineDatum(d2) = o2.datum
    expect n2: RegistryNode = d2
    valid_insertion(old, n1, n2, key) || valid_insertion(old, n2, n1, key)
  }

  else(_) {
    fail
  }
}

fn is_node(out: Output, node_policy: PolicyId) -> Bool {
  assets.quantity_of(out.value, node_policy, node_token_name) >= 1 && out.reference_script == None
}

// ----------------------------------------------------------------------------
// Tests
// ----------------------------------------------------------------------------

const node_policy: PolicyId =
  #"99999999999999999999999999999999999999999999999999999999"

const hash_a: ByteArray =
  #"0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a"

const hash_m: ByteArray =
  #"5050505050505050505050505050505050505050505050505050505050505050"

const hash_z: ByteArray =
  #"f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0"

fn script_address() -> address.Address {
  address.from_script(
    #"77777777777777777777777777777777777777777777777777777777",
  )
}

fn node_value() -> assets.Value {
  assets.merge(
    assets.from_lovelace(2_000_000),
    assets.from_asset(node_policy, node_token_name, 1),
  )
}

fn node_output(n: RegistryNode) -> Output {
  Output {
    address: script_address(),
    value: node_value(),
    datum: InlineDatum(n),
    reference_script: None,
  }
}

fn own_ref() -> OutputReference {
  OutputReference {
    transaction_id: #"0000000000000000000000000000000000000000000000000000000000000000",
    output_index: 0,
  }
}

fn spend_tx(spent: RegistryNode, outputs: List<Output>) -> Transaction {
  let own_input =
    Input { output_reference: own_ref(), output: node_output(spent) }
  Transaction { ..placeholder, inputs: [own_input], outputs: outputs }
}

test inserts_a_new_hash() {
  let anchor_old = RegistryNode { key: hash_a, next: hash_z }
  let anchor_new = RegistryNode { key: hash_a, next: hash_m }
  let inserted = RegistryNode { key: hash_m, next: hash_z }
  proof_registry.spend(
    node_policy,
    Some(anchor_old),
    InsertHash { key: hash_m },
    own_ref(),
    spend_tx(anchor_old, [node_output(anchor_new), node_output(inserted)]),
  )
}

test rejects_duplicate_hash() {
  // hash_a already bounds the gap; inserting hash_a is not strictly between -> rejected.
  let anchor_old = RegistryNode { key: hash_a, next: hash_z }
  let anchor_new = RegistryNode { key: hash_a, next: hash_a }
  let inserted = RegistryNode { key: hash_a, next: hash_z }
  !proof_registry.spend(
    node_policy,
    Some(anchor_old),
    InsertHash { key: hash_a },
    own_ref(),
    spend_tx(anchor_old, [node_output(anchor_new), node_output(inserted)]),
  )
}

test rejects_missing_node_token() fail {
  let anchor_old = RegistryNode { key: hash_a, next: hash_z }
  let anchor_new = RegistryNode { key: hash_a, next: hash_m }
  let inserted = RegistryNode { key: hash_m, next: hash_z }
  let bad =
    Output { ..node_output(inserted), value: assets.from_lovelace(2_000_000) }
  proof_registry.spend(
    node_policy,
    Some(anchor_old),
    InsertHash { key: hash_m },
    own_ref(),
    spend_tx(anchor_old, [node_output(anchor_new), bad]),
  )
}
