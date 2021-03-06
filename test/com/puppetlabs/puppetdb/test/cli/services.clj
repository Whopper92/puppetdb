(ns com.puppetlabs.puppetdb.test.cli.services
  (:require [com.puppetlabs.utils :as utils])
  (:use [com.puppetlabs.puppetdb.cli.services]
        [clojure.test]))

(deftest commandproc-configuration
  (testing "should use the thread value specified"
    (let [config (configure-commandproc-threads {:command-processing {:threads 37}})]
      (is (= (get-in config [:command-processing :threads]) 37))))

  (let [with-ncores (fn [cores]
                      (with-redefs [utils/num-cpus (constantly cores)]
                        (-> (configure-commandproc-threads {})
                            (get-in [:command-processing :threads]))))]
    (testing "should default to half the available CPUs"
      (is (= (with-ncores 4) 2)))
    (testing "should default to half the available CPUs, rounding down"
      (is (= (with-ncores 5) 2)))
    (testing "should default to half the available CPUs, even on single core boxes"
      (is (= (with-ncores 1) 1)))))

(deftest database-configuration
  (testing "database"
    (testing "should use the value specified"
      (let [config (configure-database {:database {:classname "something"}})]
        (is (= (get-in config [:database :classname]) "something"))
        (is (nil? (get-in config [:database :subprotocol])))
        (is (nil? (get-in config [:database :subname])))))

    (testing "should default to hsqldb"
      (let [config (configure-database {:global {:vardir "/var/lib/puppetdb"}})
            expected {:classname "org.hsqldb.jdbcDriver"
                      :subprotocol "hsqldb"
                      :subname "file:/var/lib/puppetdb/db;hsqldb.tx=mvcc;sql.syntax_pgs=true"}]
        (is (= (dissoc (:database config) :gc-interval)
               expected)))))

  (testing "gc-interval"
    (testing "should use the value specified"
      (let [config (configure-database {:database {:gc-interval 900}})]
        (is (= (get-in config [:database :gc-interval]) 900))))

    (testing "should default to 60 minutes"
      (let [config (configure-database {})]
        (is (= (get-in config [:database :gc-interval]) 60))))))

(deftest http-configuration
  (testing "should enable need-client-auth"
    (let [config (configure-web-server {:jetty {:client-auth false}})]
      (is (= (get-in config [:jetty :client-auth]) :need)))))

(deftest vardir-validation
  (testing "should fail if it's not specified"
    (is (thrown-with-msg? IllegalArgumentException #"is not specified"
          (validate-vardir nil))))

  (testing "should fail if it's not an absolute path"
    (is (thrown-with-msg? IllegalArgumentException #"must be an absolute path"
          (validate-vardir "foo/bar/baz"))))

  (testing "should fail if it doesn't exist"
    (is (thrown-with-msg? java.io.FileNotFoundException #"does not exist"
          (validate-vardir "/abc/def/ghi"))))

  (testing "should fail if it's not a directory"
    (let [filename (doto (java.io.File/createTempFile "not_a" "directory")
                     (.deleteOnExit))]
      (is (thrown-with-msg? java.io.FileNotFoundException #"is not a directory"
            (validate-vardir filename)))))

  (testing "should fail if it's not writable"
    (let [filename (doto (java.io.File/createTempFile "not" "writable")
                     (.deleteOnExit)
                     (.delete)
                     (.mkdir)
                     (.setReadOnly))]
      (is (thrown-with-msg? java.io.FileNotFoundException #"is not writable"
            (validate-vardir filename)))))

  (testing "should return the value if everything is okay"
    (let [filename (doto (java.io.File/createTempFile "totally" "okay")
                     (.deleteOnExit)
                     (.delete)
                     (.mkdir)
                     (.setWritable true))]
      (is (= (validate-vardir filename) filename)))))
