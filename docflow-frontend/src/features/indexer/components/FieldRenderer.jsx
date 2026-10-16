import {
  TextInput,
} from "../../../components/common/form";

function getFieldConfig(field) {
  const key = field.toLowerCase();

  // Date fields
  if (key.includes("date")) {
    return {
      type: "text",
      label: field,
      placeholder: "YYYY-MM-DD",
    };
  }

  // Numeric values only
  if (
    key.includes("amount") ||
    key.includes("sum") ||
    key.includes("quantity") ||
    key.includes("count")
  ) {
    return {
      type: "number",
      label: field,
    };
  }

  // Everything else
  return {
    type: "text",
    label: field,
  };
}

function formatLabel(field) {
  return field
    .replace(/([A-Z])/g, " $1")
    .replace(/^./, (char) => char.toUpperCase());
}

function FieldRenderer({
  control,
  field,
}) {
  const config = getFieldConfig(field);

  return (
    <TextInput
      control={control}
      name={field}
      label={formatLabel(config.label)}
      type={config.type}
      placeholder={config.placeholder}
    />
  );
}

export default FieldRenderer;