CREATE TABLE IF NOT EXISTS deadlock_test (
    id INT PRIMARY KEY,
    val TEXT
);

INSERT INTO deadlock_test (id, val) VALUES (1, 'A'), (2, 'B') ON CONFLICT (id) DO NOTHING;
