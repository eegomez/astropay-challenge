Context Overview


We are building the world’s first truly global wallet, enabling digital nomads and global users to conduct a wide array of financial transactions, including withdrawals, peer-to-peer transfers, wallet top-ups, cryptocurrency operations, bill payments, debit card usage, and more. Each transaction type could be processed by different microservices within our ecosystem.


The Challenge


Our users interact with a growing number of products (Card, P2P, Earnings, Crypto), but their transaction history is often fragmented. This creates a confusing experience as transactions originate from diverse source microservices across our platform.


Design a scalable, real-time system to consolidate all financial transactions into a single, unified "Activity Feed." This feed must be instantly searchable, filterable by standard dimensions (product, currency, status, etc.), and efficiently paginated. Crucially, different transaction types may have unique, custom metadata (e.g., the merchant details for a card payment, or the peer's information for a P2P transfer). Your search solution must be able to query these custom fields efficiently.


Your design should detail the architecture for gathering this data, the storage strategy for fast reads, and the API contract for the frontend clients While app internal architecture is beyond the scope of the current challenge, you are expected to create API contracts that support an excellent UX for the user
