BEGIN;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM inventory) OR EXISTS (SELECT 1 FROM reservations) OR EXISTS (SELECT 1 FROM request_results) THEN
    RAISE EXCEPTION 'Refusing to seed nonempty Project 20 data';
  END IF;
  INSERT INTO inventory(product_code,initial_quantity,on_hand,held)
  VALUES ('DEMO-RED',10,10,0),('DEMO-BLUE',10,10,0),('DEMO-GREEN',10,10,0);
END $$;
COMMIT;
