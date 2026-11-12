-- Hashes are now stored as {bcrypt}$2a$..., so the algorithm can be changed later
-- without breaking existing users. Existing rows are plain BCrypt hashes.
UPDATE users
SET password = '{bcrypt}' || password
WHERE password NOT LIKE '{%';
