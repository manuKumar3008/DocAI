const router = require("express").Router();
const ctrl = require("../controllers/conversation.controller");

router.get("/", ctrl.listConversations);
router.post("/", ctrl.createConversation);
router.get("/:id", ctrl.getConversation);
router.post("/:id/ask", ctrl.askQuestion);
router.get("/:id/messages", ctrl.getMessages);

module.exports = router;
