const router = require("express").Router();
const auth = require("../controllers/auth.controller");

router.post("/login", auth.login);
router.get("/me", auth.me);
router.post("/logout", auth.logout);
router.put("/change-password", auth.changePassword);

module.exports = router;
