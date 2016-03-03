(ns sf-clojure.sf-api-client
  (:gen-class))

(require '[org.httpkit.client :as http])
(require '[clojure.data.json :as json])
(require '[aleph.http :as ws-http])
(require '[byte-streams :as bs])
(require '[manifold.stream :as s])

(require '[sf-clojure.secrets :as secrets])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;; Static, GM, and Config Stuff ;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; Make these private definitions?
;

(def api-key secrets/api-key)
(def base-url "https://api.stockfighter.io/ob/api")

(def req-opts {
	:headers {"X-Starfighter-Authorization" api-key}
	:user-agent "Invisible Hand, Left"})


(def instance-id (atom nil))
(def account (atom ""))
(def instructions (atom ""))
(def venues (atom []))
(def tickers (atom []))
(def secs-per-day (atom nil))

(def eow-day (atom 0))
(def current-day (atom 0))
(def target-price (atom nil))

(def last-quote (atom nil))
(def execution-list (atom []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;; GM Functions ;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-game [game-map]
	(do 
		(println "Assigning Level Atoms")
		(reset! instance-id (get game-map "instanceId"))
		(reset! account (get game-map "account"))
		; Not sure why instructions is nested?
		(reset! instructions (get-in game-map ["instructions" "Instructions"]))
		(reset! venues (get game-map "venues"))
		(reset! tickers (get game-map "tickers"))
		(reset! secs-per-day (get game-map "secondsPerTradingDay"))
		(get game-map "ok")
	))

(defn stop-game [inst-id]
	"Ends the game, does not pause"
	(http/post (str "https://www.stockfighter.io/gm/instances/" inst-id "/stop") req-opts))

(defn restart-game [inst-id]
	(start-game (:body @(http/post (str "https://www.stockfighter.io/gm/instances/" inst-id "/restart") req-opts))))

(defn resume-game [inst-id]
	"Really just re-sends the game info again, stop-game doesn't pause"
	(http/post (str "https://www.stockfighter.io/gm/instances/" inst-id "/resume") req-opts))

(defn start-chockablock []
	(start-game (json/read-str (:body @(http/post "https://www.stockfighter.io/gm/levels/chock_a_block" req-opts)))))

 (defn check-on-instance []
 	(http/get (str "https://www.stockfighter.io/gm/instances/" @instance-id)))

 (defn extract-price [text]
 	(Double/parseDouble (second (clojure.string/split text #"was: \$"))))

 (defn process-instance-info [resp]
 	(let [data (json/read-json (:body @resp))]
 		(if (and (not target-price) (:flash data))
 			(reset! target-price (extract-price (:flash data))))
 		(reset! current-day (:tradingDay (:details data)))
 		(reset! eow-day (:endOfTheWorldDay (:details data)))
 		))

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
	(http/post 
		(str "https://api.stockfighter.io/ob/api/venues/" (get order "venue") "/stocks/" (get order "stock") "/orders")
		(into req-opts {:body (json/write-str order)}))
)

(defn fok-last-quote [] 
	(println "ATTEMPTING FOK ORDER: ")
	(place-order (into default-order {
			"direction" "buy"
			"venue" (get-in @last-quote ["quote" "venue"])
			"stock" (get-in @last-quote ["quote" "symbol"])
			"account" @account
			"orderType" "fill-or-kill"
			"qty" (get-in @last-quote ["quote" "askSize"])
			"price" (get-in @last-quote ["quote" "ask"])
			})))

(defn ioc-last-quote [] 
	(println "ATTEMPTING FOK ORDER: ")
	(place-order (into default-order {
			"direction" "buy"
			"venue" (get-in @last-quote ["quote" "venue"])
			"stock" (get-in @last-quote ["quote" "symbol"])
			"account" @account
			"orderType" "immediate-or-cancel"
			"qty" (get-in @last-quote ["quote" "askSize"])
			"price" (get-in @last-quote ["quote" "ask"])
			})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;; WEB SOCKETS ;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
			 "wss://api.stockfighter.io/ob/api/ws/:trading_account/venues/:venue/executions/stocks/:symbol"
		(str "wss://api.stockfighter.io/ob/api/ws/" acct "/venues/" venue "/executions/")))

(defn mk-execution-ticker
	[acct venue stock]
	(ws-http/websocket-client 
		; Not sure why www worked instead of api....
		(str "wss://www.stockfighter.io/ob/api/ws/" acct "/venues/" venue "/executions/stocks/" stock)))


(defn record-last-quote [ws]
	(s/consume (fn [q] (reset! last-quote (json/read-str q))) @ws))

(defn record-executions [ws]
	(s/consume (fn [q] (println q) (swap! execution-list conj (json/read-str q))) @ws))

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

(defn get-qty [ordr]
	"It's gross yes! but it'll be cleaned when I add records for orders"
	(reduce + (map #(get % "qty") (get-in ordr ["order" "fills"]))))

(defn total-purchased [executions]
	(reduce + (map get-qty executions)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;; COMPOSITES ;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn stupid-block-sell [acct venue stock qty]
	"This method assumes your fellow market participants are criminally stupid."
	(println "Starting up....")
	(let [order-complete-or-failed (atom false)
		lq-time (atom nil)]
		(while (> qty (total-purchased @execution-list))
			; (println "in the loop!")
			; (println (str @lq-time  "\t\t" (get-in last-quote["quote" "quoteTime"])))
			(if (and @last-quote (not= @lq-time (get-in @last-quote ["quote" "quoteTime"])) (get-in @last-quote ["quote" "ask"]))
				(do
					(println "\n\n\n")
					(println @(ioc-last-quote))
					(println (str "Needed: " qty "\t" "Purchased: " (total-purchased @execution-list)))
					(process-instance-info (check-on-instance))
					(reset! lq-time (get-in @last-quote ["quote" "quoteTime"])))))))

; Instead of FOK how about maintaining n number of shares at target price?

; EXAMPLE USAGE

;(load "sf-api-client")
;(require ['sf-clojure.sf-api-client :refer :all])
;(require '[org.httpkit.client :as http])
;(require '[clojure.data.json :as json])
;(require '[aleph.http :as ws-http])
;(require '[byte-streams :as bs])
;(require '[manifold.stream :as s])
;(require '[sf-clojure.secrets :as secrets])
;
;(start-chockablock)
;
;(def ws (mk-ticker @account (first @venues) (first @tickers)))
;
;(def ex-ws (mk-execution-ticker @account (first @venues) (first @tickers)))
;
;(record-last-quote ws)
;(record-executions ex-ws)
;
;(stupid-block-sell @account (first @venues) (first @tickers) 100000)






