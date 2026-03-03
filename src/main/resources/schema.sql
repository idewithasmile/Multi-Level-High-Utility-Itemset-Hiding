DROP TABLE IF EXISTS transaction_items;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS sensitive_itemsets;
DROP TABLE IF EXISTS taxonomy;
DROP TABLE IF EXISTS external_utilities;

CREATE TABLE taxonomy (
    parent VARCHAR(64) NOT NULL,
    child VARCHAR(64) NOT NULL,
    PRIMARY KEY (parent, child)
);

CREATE TABLE external_utilities (
    item VARCHAR(64) PRIMARY KEY,
    eu DOUBLE NOT NULL
);

CREATE TABLE transactions (
    tid INT PRIMARY KEY
);

CREATE TABLE transaction_items (
    tid INT NOT NULL,
    item VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (tid, item),
    FOREIGN KEY (tid) REFERENCES transactions(tid)
);

CREATE TABLE sensitive_itemsets (
    name VARCHAR(64) PRIMARY KEY,
    items VARCHAR(512) NOT NULL
);

-- Taxonomy: Food -> {X, Y, Z}; X -> {A, B}; Y -> {C}; Z -> {D, E}
INSERT INTO taxonomy(parent, child) VALUES ('Food', 'X');
INSERT INTO taxonomy(parent, child) VALUES ('Food', 'Y');
INSERT INTO taxonomy(parent, child) VALUES ('Food', 'Z');
INSERT INTO taxonomy(parent, child) VALUES ('X', 'A');
INSERT INTO taxonomy(parent, child) VALUES ('X', 'B');
INSERT INTO taxonomy(parent, child) VALUES ('Y', 'C');
INSERT INTO taxonomy(parent, child) VALUES ('Z', 'D');
INSERT INTO taxonomy(parent, child) VALUES ('Z', 'E');

-- External utilities (profits)
INSERT INTO external_utilities(item, eu) VALUES ('A', 2.0);
INSERT INTO external_utilities(item, eu) VALUES ('B', 1.0);
INSERT INTO external_utilities(item, eu) VALUES ('C', 2.0);
INSERT INTO external_utilities(item, eu) VALUES ('D', 3.0);
INSERT INTO external_utilities(item, eu) VALUES ('E', 4.0);

-- Transactions
INSERT INTO transactions(tid) VALUES (1);
INSERT INTO transactions(tid) VALUES (2);
INSERT INTO transactions(tid) VALUES (3);
INSERT INTO transactions(tid) VALUES (4);

INSERT INTO transaction_items(tid, item, quantity) VALUES (1, 'A', 2);
INSERT INTO transaction_items(tid, item, quantity) VALUES (1, 'C', 3);

INSERT INTO transaction_items(tid, item, quantity) VALUES (2, 'B', 3);
INSERT INTO transaction_items(tid, item, quantity) VALUES (2, 'D', 1);
INSERT INTO transaction_items(tid, item, quantity) VALUES (2, 'E', 1);

INSERT INTO transaction_items(tid, item, quantity) VALUES (3, 'A', 8);
INSERT INTO transaction_items(tid, item, quantity) VALUES (3, 'D', 2);
INSERT INTO transaction_items(tid, item, quantity) VALUES (3, 'E', 1);
INSERT INTO transaction_items(tid, item, quantity) VALUES (3, 'C', 1);

INSERT INTO transaction_items(tid, item, quantity) VALUES (4, 'B', 4);
INSERT INTO transaction_items(tid, item, quantity) VALUES (4, 'C', 1);
INSERT INTO transaction_items(tid, item, quantity) VALUES (4, 'E', 1);

-- Sensitive itemset (generalized): {X, E, D, C}
INSERT INTO sensitive_itemsets(name, items) VALUES ('S1', 'X,E,D,C');
