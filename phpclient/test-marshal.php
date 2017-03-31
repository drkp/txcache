<?php

var_dump(txcache_test_marshal(null));
var_dump(txcache_test_marshal(true));
var_dump(txcache_test_marshal(false));
var_dump(txcache_test_marshal(42));
var_dump(txcache_test_marshal(42.0));
var_dump(txcache_test_marshal("abcdef"));
var_dump(txcache_test_marshal(array("a" => "b", "c" => "d")));
var_dump(txcache_test_marshal(array("a" => "b", 1 => "2")));

$everything = array("null" => null,
                    "bool(true)" => true,
                    "bool(false)" => false,
                    "long(42)" => 42,
                    "float(42.5)" => 42.5,
                    "string(3)" => "a\0b",
                    "array" => array("a" => "b", 1 => "2"),
                    "nast\0ykey" => 10);

var_dump(txcache_test_marshal($everything));

?>
