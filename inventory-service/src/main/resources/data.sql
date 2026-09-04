-- Seed data for the Saga demo. Runs once at each startup because the
-- inventory DB is in-memory (H2) and is recreated every time the service starts.
MERGE INTO products (product_code, product_name, stock_quantity) KEY (product_code) VALUES ('ITEM123', 'Wireless Mouse', 10);
MERGE INTO products (product_code, product_name, stock_quantity) KEY (product_code) VALUES ('ITEM456', 'Mechanical Keyboard', 5);
