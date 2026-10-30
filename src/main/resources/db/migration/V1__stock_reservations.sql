CREATE TABLE inventory (
  product_code varchar(32) PRIMARY KEY,
  initial_quantity integer NOT NULL CHECK (initial_quantity BETWEEN 0 AND 1000000),
  on_hand integer NOT NULL,
  held integer NOT NULL,
  CHECK (0 <= held AND held <= on_hand AND on_hand <= initial_quantity)
);

CREATE TABLE reservations (
  id uuid PRIMARY KEY,
  order_reference varchar(64) NOT NULL CHECK (order_reference ~ '^[A-Za-z0-9._:-]{1,64}$'),
  product_code varchar(32) NOT NULL REFERENCES inventory(product_code),
  quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
  state varchar(16) NOT NULL CHECK (state IN ('ACTIVE','CONFIRMED','CANCELLED','EXPIRED')),
  created_at timestamptz NOT NULL,
  expires_at timestamptz NOT NULL,
  decided_at timestamptz,
  CHECK (expires_at = created_at + interval '5 minutes'),
  CHECK ((state = 'ACTIVE' AND decided_at IS NULL) OR
         (state <> 'ACTIVE' AND decided_at IS NOT NULL AND decided_at >= created_at)),
  CHECK (state NOT IN ('CONFIRMED','CANCELLED') OR decided_at < expires_at),
  CHECK (state <> 'EXPIRED' OR decided_at >= expires_at)
);
CREATE INDEX reservations_product_state_idx ON reservations(product_code,state);

CREATE TABLE request_results (
  request_key varchar(128) PRIMARY KEY CHECK (request_key ~ '^[A-Za-z0-9._:-]{1,128}$'),
  canonical_identity text NOT NULL,
  response_status integer,
  response_body text,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  CHECK ((response_status IS NULL AND response_body IS NULL) OR
         (response_status BETWEEN 200 AND 499 AND response_body IS NOT NULL))
);
