const router = require("express").Router();
const ctrl = require("../controllers/document.controller");
const upload = require("../middleware/upload.middleware");

router.get("/", ctrl.listDocuments);
router.post("/", upload.single("file"), ctrl.uploadDocument);
router.get("/:documentId", ctrl.getDocument);
router.get("/:documentId/file", ctrl.getDocumentFile);
router.delete("/:documentId", ctrl.deleteDocument);
router.post("/:documentId/summarize", ctrl.summarizeDocument);
router.post("/:documentId/translate", ctrl.translateDocument);
router.post("/:documentId/translate/stream", ctrl.translateDocumentStream);

module.exports = router;
