const { DocumentType } = require("../models");

// Attaches doctype-level requiredFields/optionalFields (from DocumentType)
// plus the document's own extractedFields/fieldConfidence to each item.
// Batch-fetches DocumentType defs to avoid N+1 queries.
const enrichDocumentFields = async (items) => {
  if (!items || items.length === 0) {
    return items;
  }

  const distinctTypes = [
    ...new Set(items.map((doc) => doc.documentType).filter(Boolean)),
  ];

  let typeDefMap = new Map();
  if (distinctTypes.length > 0) {
    const typeDefs = await DocumentType.find({
      documentType: { $in: distinctTypes },
    })
      .select("documentType mandatoryFields optionalFields")
      .lean();

    typeDefMap = new Map(typeDefs.map((t) => [t.documentType, t]));
  }

  return items.map((doc) => {
    const plain = doc.toObject ? doc.toObject() : doc;
    const def = plain.documentType ? typeDefMap.get(plain.documentType) : null;

    return {
      ...plain,
      requiredFields: def?.mandatoryFields ?? [],
      optionalFields: def?.optionalFields ?? [],
      extractedFields: plain.extractedFields ?? {},
      fieldConfidence: plain.fieldConfidence ?? {},
    };
  });
};

module.exports = { enrichDocumentFields };
