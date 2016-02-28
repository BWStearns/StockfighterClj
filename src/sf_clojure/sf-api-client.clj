(ns sf-clojure.sf-api-client
  (:gen-class))

(require '[org.httpkit.client :as http])
(require '[clojure.data.json :as json])
(require '[aleph.http :as ws-http])
(require '[byte-streams :as bs])
(require '[manifold.stream :as s])

(require '[sf-clojure.secrets :as secrets])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;; Static and Config Stuff ;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; Make these private definitions
;

(def api-key secrets/api-key)
(def base-url "https://api.stockfighter.io/ob/api")

(def req-opts {
	:headers {"X-Starfighter-Authorization" api-key}
	:user-agent "Invisible Hand, Left"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;; GM Functions ;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-game [lvl]
	(println lvl " Shit!")
	)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;; Endpoints ;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; Getting Info

(defn sf-server-is-alive []
	"Verifies that the game server is still working"
	(let 
		[{:keys [status headers body error] :as resp} @(http/get "https://api.stockfighter.io/ob/api/heartbeat")]
		(= (:ok (json/read-json body)) true)))

(defn get-quote
	[venue stock]
	; https://api.stockfighter.io/ob/api/venues/:venue/stocks/:stock/quote
	(json/read-json (:body @(http/get (str base-url "/venues/" venue "/stocks/" stock "/quote")))))

(defn order-status
	[order]
	"Expects the JSONed order map"
	(json/read-json (
		:body @(http/get 
			(str base-url "venues/" (:venue order) "/stocks/" (:symbol order) "/orders/" (:id order))
			req-opts))))

(defn all-order-statuses
	[venue account] 
	(json/read-json (
		:body @(http/get 
			(str base-url "/venues/" venue "/accounts/" account "/orders")
			req-opts
			))))

; Placing orders

(def default-order {
	"account" nil
	"venue" nil
	"stock" nil
	"price" nil
	"qty" nil
	"direction" nil
	"orderType" nil
	})

(defn place-order
	[order]
	"
	account: String 'EXB123456'
	venue: String 'TESTEX'
	stock: 'FOOBAR'
	price: Integer, Ignored for market orders.
	qty: Integer
	direction: Whether you want to 'buy' or 'sell'
	orderType: ['limit'|'market'|'fill-or-kill'|'immediate-or-cancel']
	"
	; https://api.stockfighter.io/ob/api/venues/:venue/stocks/:stock/orders
	@(http/post 
		(str "https://api.stockfighter.io/ob/api/venues/" (get order "venue") "/stocks/" (get order "stock") "/orders")
		(into req-opts {:body (json/write-str order)}))
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;; WEB SOCKETS ;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; Probably going to need s/consume?

(def venue "NIIGEX")
(def account "SY44249583")
(def last-quote (atom nil))

(defn mk-ticker
	[acct venue stock]
	(ws-http/websocket-client 
		(str "wss://api.stockfighter.io/ob/api/ws/" acct "/venues/" venue "/tickertape/stocks/" stock)))

(defn mk-ex-ticker
	[acct venue]
	(println "unimplemented"))

(defn mk-ex-execution-ticker
	[acct venue]
	(ws-http/websocket-client 
		(str "wss://api.stockfighter.io/ob/api/ws/" acct "/venues/" venue "/executions/")))

(defn mk-execution-ticker
	[acct venue stock]
	(ws-http/websocket-client 
		(str "wss://api.stockfighter.io/ob/api/ws/" acct "/venues/" venue "/executions/stocks/" stock)))

(defn record-to [record-atom]
	; Switch to update instead of replace with last
	(fn [msg]
		(reset! record-atom msg)))

; Lead for dealing with breaking pipes: s/closed? || s/on-closed
(def ws (mk-ex-ticker account venue))

(defn record-venue [record connection]
	(future (s/consume (record-to record) @connection)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;; ATOMIC STATS ;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn spread [stock-quote] (- 
	(or (get stock-quote "ask") (get stock-quote "bid") 0)
	(or (get stock-quote "bid") (get stock-quote "ask") 0)))

(defn average [lst] (/ (reduce + lst) (count lst)))

(defn moving-average [window lst] (map average (partition-all window 1 lst)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;; ORDER MANIPULATION ;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn filter-open-orders
	[orders] (filter #(= (:open %) true)))

(defn order-is-stale
	[stock-quote order]
	; placeholder. Maybe if the distance from the best price is more than the Zx spread?
		(false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;; COMPOSITES ;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(let [foo 3 bar 6 baz (fn [] (* bar 10)) a 5] (baz))


(defn stupid-block-sell [acct venue stock qty]
	"This method assumes your fellow market participants are criminally stupid."
	(let [
		; State tracking shit
		order-complete-or-failed (atom false)
		remaining-to-sell (atom qty)
		lq-time (atom nil)
		fok-order (into default-order {
			"direction" "buy"
			"venue" venue
			"stock" stock
			"account" acct
			"orderType" "fill-or-kill"
			})
		get-all-orders (fn [] (all-order-statuses venue acct))
		]
		(while (not order-complete-or-failed)
			(let [lq (json/read-str @last-quote)]

				(if (and lq (not= lq-time (get lq "quoteTime")))
					(
						(reset! lq-time (get lq "quoteTime"))
						(println (place-order (into fok-order {:price (get lq "bid") :qty (get lq "bidSize")}))))
					(
						println "Do Nothing")
					)
						))))






