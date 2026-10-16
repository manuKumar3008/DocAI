const router = require("express").Router();
const ctrl = require("../controllers/indexing.controller");

router.get("/queue", ctrl.getQueue);
router.get("/:documentId", ctrl.getIndexingDetail);
router.post("/:documentId/submit", ctrl.submitIndexing);

module.exports = router;
