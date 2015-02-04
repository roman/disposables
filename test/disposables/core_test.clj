(ns disposables.core-test
  (:require [clojure.test :refer :all]
            [disposables.core :refer :all]))

(deftest disposable
  (testing "dispose"
    (testing "executes disposable action only once"
      (let [acc (atom 0)
            disposable (new-disposable "test"
                                       (swap! acc inc))]
        (dotimes [n 100]
          (dispose disposable))
        (is (= 1 @acc) "err: disposable executed more than once"))))

  (testing "verbose-dispose"
    (testing "returns exceptions on dispose"
      (let [exception (Exception. "EXCEPTION")
            disposable (new-disposable "test" (throw exception))
            result (verbose-dispose disposable)]
        (is (= ["test" exception] (first result))
            "err: verbose-dispose doesn't return error"))))

  (testing "mappend"
    (testing "merges multiple disposables into one"
      (let [exception (Exception. "EXCEPTION")
            d1 (new-disposable "test-1" (do nil))
            d2 (new-disposable "test-2" (throw exception))
            disposable (reduce mappend empty-disposable [d1 d2])
            result (verbose-dispose disposable)]
        (is (= (count result) 2)
            "err: disposable result count is different from merged ones")

        (is (= ["test-2" exception]
               (nth result 0))
            "err: disposables are not merged in reversed order")

        (is (= ["test-1" true]
               (nth result 1)))))))

(deftest single-assignment-disposable
  (testing "dispose"
    (testing "disposes inner-disposable correctly"
      (let [acc (atom 0)
            disposable (new-single-assignment-disposable)
            inner      (new-disposable "inner" (swap! acc inc))]
        (set-disposable disposable inner)
        (dotimes [_ 100]
          (dispose disposable))
        (is (= @acc 1)
            "err: single-assignment-disposable disposes more than once")))

    (testing "fails when inner-disposable not present"
      (let [disposable (new-single-assignment-disposable)
            result (verbose-dispose disposable)]
        (is (= sad-not-initialized-error
               (first result))))))

  (testing "set-disposable"
    (testing "throws an error when called more than once"
      (let [disposable (new-single-assignment-disposable)
            inner (new-disposable "inner" (do nil))]
        (set-disposable disposable inner)
        (try
          (set-disposable disposable inner)
          (catch Exception e
            (is (= sad-double-assignment-error e))))))))

(deftest serial-disposable
  (testing "dispose"
    (testing "ignores dispose action when inner-disposable not present"
      (let [disposable (new-serial-disposable)
            result (verbose-dispose disposable)]
        (is (= sd-empty-disposable-response
               (first result))))))

  (testing "set-disposable"
    (testing "disposes previous inner-disposable if called more than once"
      (let [acc (atom 0)
            d1  (new-disposable "acc-disposable-1" (swap! acc inc))
            d2  (new-disposable "acc-disposable-2" (swap! acc inc))
            disposable (new-serial-disposable)]
        (set-disposable disposable d1)
        (set-disposable disposable d2)
        (is (= @acc 1))))))