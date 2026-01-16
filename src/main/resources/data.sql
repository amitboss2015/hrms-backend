
INSERT INTO shifts (id, code, name, start_time, end_time, break_mins, grace_in_mins, grace_out_mins, boundary_after_midnight_mins, rounding, halfday_threshold_mins, min_work_mins, effective_from, mon,tue,wed,thu,fri,sat,sun, active)
VALUES (1,'MORNING','Morning','09:00:00','17:30:00',30,5,10,90,'NEAREST_5',240,0,'2024-01-01',true,true,true,true,true,true,true,true)
ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO shifts (id, code, name, start_time, end_time, break_mins, grace_in_mins, grace_out_mins, boundary_after_midnight_mins, rounding, halfday_threshold_mins, min_work_mins, effective_from, mon,tue,wed,thu,fri,sat,sun, active)
VALUES (2,'EVENING','Evening','17:30:00','00:30:00',15,5,10,90,'NEAREST_5',240,0,'2024-01-01',true,true,true,true,true,true,true,true)
ON DUPLICATE KEY UPDATE name=VALUES(name);
