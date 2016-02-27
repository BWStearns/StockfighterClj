(ns sf-clojure.sf_api_client
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

(defn sf-server-is-alive []
	"Verifies that the game server is still working"
	(let 
		[{:keys [status headers body error] :as resp} @(call-api :is-alive)]
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

(defn order-is-stale
	[stock-quote order]
	; placeholder. Maybe if the distance from the best price is more than the Zx spread?
		(false))

(defn all-order-statuses
	[venue account] 
	(json/read-json (
		:body @(http/get 
			(str base-url "/venues/" venue "/accounts/" account "/orders")
			req-opts
			))))

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
	(ws-http/w

(defn mk-ex-execution-ticker
	[acct venue]
	(ws-http/websocket-client 
		(str "wss://api.stockfighter.io/ob/api/ws/" acct "/venues/" venue "/executions/")))

(defn mk-execution-ticker
	[acct venue stock]
	(ws-http/websocket-client 
		(str "wss://api.stockfighter.io/ob/api/ws/" acct "/venues/" venue "/executions/stocks/" stock)))

(def record-to [record-atom]
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
	(or (get stock-quote "ask") (get stock-quote "bid"))
	(or (get stock-quote "bid") (get stock-quote "ask"))))

(defn average [lst] (/ (reduce + lst) (count lst)))

(defn moving-average [window lst] (map average (partition-all window 1 lst)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;; ORDER MANIPULATION ;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn filter-open-orders
	[orders] (filter #(= (:open %) true)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;; COMPOSITES ;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


; (defn block-sell [acct venue stock qty price-tolerance]
; 	"Price tolerance is the % increase to tolerate before letting the market cool off.
; 	Histories must be most recent at 0 index"
; 	(let [
; 		; State tracking shit
; 		order-complete-or-failed (atom false)
; 		ticker-history (atom [])
; 		open-orders (atom [])
; 		remaining-to-sell (atom qty)
; 		; Save some parens
; 		get-all-orders #(all-order-statuses venue acct)
; 		]

; 		(while (not order-complete-or-failed)
; 			(let [
; 				current-quote (get-quote venue stock)
; 				orders (get-all-orders)
; 				]
				
; 				))))

(defn stupid-block-sell [acct venue stock qty]
	"This method assumes your fellow market participants are criminally stupid."
	(let [
		; State tracking shit
		order-complete-or-failed (atom false)
		remaining-to-sell (atom qty)
		; Save some parens
		get-all-orders #(all-order-statuses venue acct)
		]
		(while (not order-complete-or-failed)
			(let [
				lq (json/read-str @last-quote)
				]

						))))







