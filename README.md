# Introduction

This repository contains the tools used to distribute the Pre-Round, Crowdsale, and Bounties to the people interested in the Grapevine Project. 

## The Issues

In the three stages of the Grapevine ICO, around 16.000 investors participated, with various amounts, including the bounty programs.
To perform such amount of transfers, the following challenges have been faced: 

1. Avoid congestioning the network 
2. Pay the gas needed
3. Avoid the "nonce too low" 

When moving to the main net, several points have to be taken into account, as suggested in [this article](https://medium.com/zinc_work/a-journey-to-mainnet-83c2a67800c).
For the first two points, we explicitly decided to always wait the transaction's receipt and to get the gas estimation directly from the default library gas provider. 
This is inefficient, but it avoids to be hated by other blockchain users, since Ethereum is so slow. We process a transaction every 5 minutes to 1 hour. 

We decided to use Infura's infrastructure. Infura has the issue of Nonce Too Low, meaning that the node which the HTTP client is connecting to, may not be yet updated with the latest
block, thus implementing the eventual consistency, resulting in a complex handling of the nonce (which has to be correctly evaluated, otherwise the transasction will not probably leave the memory pool,
since the node seems to wait for transactions in order). Metamask [has a special provider](https://github.com/MetaMask/provider-engine/blob/master/subproviders/nonce-tracker.js) to deal with this problem. 
However I personally [agree with this guy](https://hackernoon.com/the-javascript-phenomenon-is-a-mass-psychosis-57adebb09359) and many others saying that
javascript is just bad. 

Therefore the flow is as follows.
1. We get the gas (updated by the default gas provider)
2. We get the nonce (from the last transaction count)
3. We sign and submit the transaction
4. We wait for the receipt
5. We check the error: if no error, the transaction is mined, otherwise, retry. The nonce is not increased (to avoid the problem above) and recursively performed. Soon the node will be updated, and it will get the transaction nonce. 

Notably that this cannot be done in parallel (e.g., using threads or asynchronous operations) since the nonce is evaluated per address. We currently run the script from four different address. 

The implementation is in Java, using web3j. 

# Authors

Massimiliano Masi / max@mascanc.net 

<a href="https://scan.coverity.com/projects/mascanc-grapevinetokendistribution">
  <img alt="Coverity Scan Build Status"
       src="https://scan.coverity.com/projects/16843/badge.svg"/>
</a>
