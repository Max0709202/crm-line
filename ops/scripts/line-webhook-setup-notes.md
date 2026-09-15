# LINE Official Account setup — per-account checklist

Each LINE Official Account (parent or child) needs the same manual setup in LINE's own
Developers Console. There is no bulk-import for this — LINE only lets the owner of each
Channel's console page configure its own webhook, one account at a time. Plan for this as
an operational task, not something the CRM can automate away — it doesn't get easier at
~100 accounts, it just needs doing ~100 times.

## Per-account setup (repeat for every account you register in LINE設定)

1. Register the account in LINE設定 (アカウント名, Channel ID, Channel Secret, Access Token).
   The system generates a unique webhook URL for it — copy it from... (see note below on
   where this will be surfaced once you have a real account to test against; for now it's
   `https://<host>/api/inbound/line/<webhookToken>`, where `webhookToken` is the random
   value stored on that account's row).
2. In the LINE Developers Console, open that Channel → Messaging API tab.
3. Paste the webhook URL into "Webhook URL" and click "Verify" — LINE sends a test call
   with an empty `events` array; the CRM responds 200 to this automatically.
4. Turn "Use webhook" ON.
5. Turn OFF auto-reply messages and the greeting message in the same console page (LINE's
   own auto-replies would otherwise compete with the CRM's).
6. Confirm "Allow bot to join group chats" is OFF unless you specifically want that — this
   integration does not manage real multi-user LINE groups (see the group-chat-mode note
   below).

## What "grows" the customer list

LINE only allows messaging a contact who has already followed the Official Account and
triggered at least one webhook event (a message, or a follow). There is no equivalent of
"type in a phone number and text them." Growing the reachable audience for a given account
means getting customers to add it as a friend — a QR code or follow-link campaign — which
is a separate marketing effort, not something this integration does for you.

## "Group LINE風モード" — what it actually is

The グループモード toggle (LINE設定 account list) does not create a real multi-person LINE
group. LINE's API has no way to programmatically create or manage a group between a
business and one customer. What it actually does: every message sent from an account with
this mode on carries a per-message sender-name/icon override (the replying staff member's
own LINE設定 → 自分のLINE表示名/アイコン), so different staff appear to the customer as
different named participants — all still within one ordinary 1:1 chat with that Official
Account. If this distinction matters for how you describe the feature to your own
customers, keep it in mind — it looks like a group to the customer, but it technically
isn't one.

## Known limitations (by design, not bugs)

- No delivery/read receipts — LINE doesn't send them to bots. A sent LINE message goes
  straight from QUEUED to SENT (or FAILED); it never reaches DELIVERED/READ.
- Broadcast sends one push call per recipient (matching how email/SMS broadcast already
  work) rather than LINE's 500-recipient multicast endpoint. Fine at current volumes;
  worth revisiting if a single broadcast's recipient count becomes large enough that
  LINE's per-call rate limits start to matter.
- "Messaging API Access Token" here means the classic long-lived channel access token
  (the one you get from the Console's own "Issue" button) — not the newer JWT-based v2.1
  scheme, which needs a private key and short-lived minted tokens instead. If your account
  only offers the v2.1 flow, this needs a follow-up change before it will work.
