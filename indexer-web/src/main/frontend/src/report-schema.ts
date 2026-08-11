import type {
  JsonSchema,
  ReportPresentation,
} from "./api/reports-api";

const JSON_SCHEMA_DIALECT = "https://json-schema.org/draft/2020-12/schema";
const MAX_PROPERTIES = 32;
const MAX_ENUM_VALUES = 32;
const MAX_RESULT_ROWS = 1_000;
const MAX_RESULT_TEXT = 65_536;

export type ScalarType = "string" | "integer" | "number" | "boolean";
export type ScalarSchema = {
  type: ScalarType | [ScalarType, "null"];
  title?: string;
  description?: string;
  format?: "date-time" | "uri";
  enum?: string[];
  default?: unknown;
  minimum?: number;
  maximum?: number;
  minLength?: number;
  maxLength?: number;
};
export type ObjectSchema = {
  $schema?: string;
  type: "object";
  title?: string;
  description?: string;
  properties: Record<string, ScalarSchema | ArraySchema>;
  required: string[];
  additionalProperties: false;
};
export type ArraySchema = {
  type: "array";
  title?: string;
  description?: string;
  items: ObjectSchema;
};
export type ValidatedReportPresentation = Omit<
  ReportPresentation,
  "parameters_schema" | "result_schema"
> & {
  parameters_schema: ObjectSchema & {
    properties: Record<string, ScalarSchema>;
  };
  result_schema: ObjectSchema;
};

export function validatePresentation(
  value: ReportPresentation,
): ValidatedReportPresentation {
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(value.name)) {
    throw new Error("Report name is missing or too long");
  }
  if (!value.title || value.title.length > 120) {
    throw new Error("Report title is missing or too long");
  }
  if (value.description.length > 500) {
    throw new Error("Report description is too long");
  }
  return {
    ...value,
    parameters_schema: validateParameterSchema(value.parameters_schema),
    result_schema: validateResultSchema(value.result_schema),
  };
}

export function scalarType(schema: ScalarSchema): ScalarType {
  return Array.isArray(schema.type) ? schema.type[0] : schema.type;
}

export function buildParameters(
  schema: ValidatedReportPresentation["parameters_schema"],
  values: Record<string, string | boolean>,
): Record<string, unknown> {
  const parameters: Record<string, unknown> = {};
  for (const [name, property] of Object.entries(schema.properties)) {
    const value = values[name];
    const required = schema.required.includes(name);
    const type = scalarType(property);
    if (type === "boolean") {
      if (required || typeof value === "boolean") {
        parameters[name] = value === true;
      }
      continue;
    }
    const text = typeof value === "string" ? value.trim() : "";
    if (!text) {
      if (required) {
        throw new Error(`${property.title ?? name} is required`);
      }
      continue;
    }
    if (type === "integer" || type === "number") {
      const number = Number(text);
      if (!Number.isFinite(number) || (type === "integer" && !Number.isInteger(number))) {
        throw new Error(`${property.title ?? name} must be a valid ${type}`);
      }
      if (property.minimum !== undefined && number < property.minimum) {
        throw new Error(`${property.title ?? name} must be at least ${property.minimum}`);
      }
      if (property.maximum !== undefined && number > property.maximum) {
        throw new Error(`${property.title ?? name} must be at most ${property.maximum}`);
      }
      parameters[name] = number;
      continue;
    }
    if (property.format === "date-time") {
      const instant = new Date(text);
      if (Number.isNaN(instant.getTime())) {
        throw new Error(`${property.title ?? name} must be a valid date and time`);
      }
      parameters[name] = instant.toISOString();
      continue;
    }
    if (property.enum && !property.enum.includes(text)) {
      throw new Error(`${property.title ?? name} has an unsupported value`);
    }
    parameters[name] = text;
  }
  return parameters;
}

export function validateResultPayload(
  schema: ObjectSchema,
  payload: Record<string, unknown>,
): void {
  validateObjectValue(schema, payload, "result");
}

function validateParameterSchema(value: JsonSchema): ValidatedReportPresentation["parameters_schema"] {
  const schema = validateObjectSchema(value, "parameters", true);
  for (const [name, property] of Object.entries(schema.properties)) {
    if (property.type === "array") {
      throw new Error(`Parameter ${name} cannot be an array`);
    }
  }
  return schema as ValidatedReportPresentation["parameters_schema"];
}

function validateResultSchema(value: JsonSchema): ObjectSchema {
  return validateObjectSchema(value, "result", false);
}

function validateObjectSchema(
  value: unknown,
  path: string,
  parameters: boolean,
): ObjectSchema {
  const source = object(value, path);
  assertKeys(
    source,
    ["$schema", "type", "title", "description", "properties", "required", "additionalProperties"],
    path,
  );
  if (source.$schema !== JSON_SCHEMA_DIALECT) {
    throw new Error(`${path} must use JSON Schema draft 2020-12`);
  }
  if (source.type !== "object" || source.additionalProperties !== false) {
    throw new Error(`${path} must be a closed object schema`);
  }
  optionalText(source.title, `${path}.title`, 120);
  optionalText(source.description, `${path}.description`, 500);
  const properties = object(source.properties, `${path}.properties`);
  const entries = Object.entries(properties);
  if (entries.length > MAX_PROPERTIES) {
    throw new Error(`${path} has too many properties`);
  }
  const required = stringArray(source.required ?? [], `${path}.required`);
  const parsed: Record<string, ScalarSchema | ArraySchema> = {};
  for (const [name, property] of entries) {
    if (!name || name.length > 128) {
      throw new Error(`${path} has an invalid property name`);
    }
    parsed[name] = validatePropertySchema(
      property,
      `${path}.${name}`,
      parameters,
    );
  }
  if (required.some((name) => !(name in parsed))) {
    throw new Error(`${path} requires an unknown property`);
  }
  return {
    $schema: JSON_SCHEMA_DIALECT,
    type: "object",
    title: source.title as string | undefined,
    description: source.description as string | undefined,
    properties: parsed,
    required,
    additionalProperties: false,
  };
}

function validatePropertySchema(
  value: unknown,
  path: string,
  parameters: boolean,
): ScalarSchema | ArraySchema {
  const source = object(value, path);
  if (source.type === "array") {
    if (parameters) {
      throw new Error(`${path} arrays are not supported as parameters`);
    }
    assertKeys(source, ["type", "title", "description", "items"], path);
    return {
      type: "array",
      title: optionalText(source.title, `${path}.title`, 120),
      description: optionalText(source.description, `${path}.description`, 500),
      items: validateRowSchema(source.items, `${path}.items`),
    };
  }
  return validateScalarSchema(source, path, parameters);
}

function validateRowSchema(value: unknown, path: string): ObjectSchema {
  const source = object(value, path);
  assertKeys(
    source,
    ["type", "title", "description", "properties", "required", "additionalProperties"],
    path,
  );
  if (source.type !== "object" || source.additionalProperties !== false) {
    throw new Error(`${path} must be a closed object schema`);
  }
  const properties = object(source.properties, `${path}.properties`);
  if (Object.keys(properties).length > MAX_PROPERTIES) {
    throw new Error(`${path} has too many properties`);
  }
  const parsed: Record<string, ScalarSchema> = {};
  for (const [name, property] of Object.entries(properties)) {
    parsed[name] = validateScalarSchema(object(property, `${path}.${name}`), `${path}.${name}`, false);
  }
  const required = stringArray(source.required ?? [], `${path}.required`);
  return {
    type: "object",
    title: optionalText(source.title, `${path}.title`, 120),
    description: optionalText(source.description, `${path}.description`, 500),
    properties: parsed,
    required,
    additionalProperties: false,
  };
}

function validateScalarSchema(
  source: Record<string, unknown>,
  path: string,
  parameters: boolean,
): ScalarSchema {
  assertKeys(
    source,
    ["type", "title", "description", "format", "enum", "default", "minimum", "maximum", "minLength", "maxLength"],
    path,
  );
  const nullable = Array.isArray(source.type);
  const type = nullable
    ? nullableType(source.type as unknown[], path)
    : source.type;
  if (!["string", "integer", "number", "boolean"].includes(String(type))) {
    throw new Error(`${path} has an unsupported scalar type`);
  }
  if (parameters && nullable) {
    throw new Error(`${path} nullable parameters are not supported`);
  }
  const format = source.format;
  if (format !== undefined && format !== "date-time" && format !== "uri") {
    throw new Error(`${path} has an unsupported format`);
  }
  if (format !== undefined && type !== "string") {
    throw new Error(`${path} format is only valid for strings`);
  }
  const enumValues = source.enum === undefined
    ? undefined
    : stringArray(source.enum, `${path}.enum`);
  if (enumValues && (type !== "string" || enumValues.length > MAX_ENUM_VALUES)) {
    throw new Error(`${path} has an unsupported enum`);
  }
  const minimum = optionalNumber(source.minimum, `${path}.minimum`);
  const maximum = optionalNumber(source.maximum, `${path}.maximum`);
  if ((minimum !== undefined || maximum !== undefined) && type !== "integer" && type !== "number") {
    throw new Error(`${path} numeric bounds require a numeric type`);
  }
  return {
    type: nullable ? [type as ScalarType, "null"] : (type as ScalarType),
    title: optionalText(source.title, `${path}.title`, 120),
    description: optionalText(source.description, `${path}.description`, 500),
    format: format as "date-time" | "uri" | undefined,
    enum: enumValues,
    default: source.default,
    minimum,
    maximum,
    minLength: optionalInteger(source.minLength, `${path}.minLength`),
    maxLength: optionalInteger(source.maxLength, `${path}.maxLength`),
  };
}

function validateObjectValue(
  schema: ObjectSchema,
  value: unknown,
  path: string,
): void {
  const record = object(value, path);
  for (const name of schema.required) {
    if (!(name in record)) {
      throw new Error(`${path}.${name} is missing`);
    }
  }
  if (Object.keys(record).some((name) => !(name in schema.properties))) {
    throw new Error(`${path} contains an unknown property`);
  }
  for (const [name, property] of Object.entries(schema.properties)) {
    if (!(name in record)) {
      continue;
    }
    const propertyValue = record[name];
    if (property.type === "array") {
      if (!Array.isArray(propertyValue)) {
        throw new Error(`${path}.${name} must be an array`);
      }
      if (propertyValue.length > MAX_RESULT_ROWS) {
        throw new Error(`${path}.${name} exceeds the result row limit`);
      }
      propertyValue.forEach((row, index) =>
        validateObjectValue(property.items, row, `${path}.${name}[${index}]`),
      );
    } else {
      validateScalarValue(property, propertyValue, `${path}.${name}`);
    }
  }
}

function validateScalarValue(
  schema: ScalarSchema,
  value: unknown,
  path: string,
): void {
  if (value === null && Array.isArray(schema.type)) {
    return;
  }
  const type = scalarType(schema);
  const valid =
    (type === "string" && typeof value === "string" && value.length <= MAX_RESULT_TEXT) ||
    (type === "boolean" && typeof value === "boolean") ||
    (type === "number" && typeof value === "number" && Number.isFinite(value)) ||
    (type === "integer" && typeof value === "number" && Number.isInteger(value));
  if (!valid) {
    throw new Error(`${path} does not match its presentation schema`);
  }
}

function nullableType(value: unknown[], path: string): ScalarType {
  if (value.length !== 2 || !value.includes("null")) {
    throw new Error(`${path} has an unsupported union type`);
  }
  const scalar = value.find((item) => item !== "null");
  if (typeof scalar !== "string") {
    throw new Error(`${path} has an unsupported union type`);
  }
  return scalar as ScalarType;
}

function object(value: unknown, path: string): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${path} must be an object`);
  }
  return value as Record<string, unknown>;
}

function assertKeys(
  source: Record<string, unknown>,
  allowed: string[],
  path: string,
): void {
  const unsupported = Object.keys(source).find((key) => !allowed.includes(key));
  if (unsupported) {
    throw new Error(`${path} uses unsupported keyword ${unsupported}`);
  }
}

function stringArray(value: unknown, path: string): string[] {
  if (!Array.isArray(value) || value.some((item) => typeof item !== "string")) {
    throw new Error(`${path} must be a string array`);
  }
  return [...value];
}

function optionalText(
  value: unknown,
  path: string,
  maximum: number,
): string | undefined {
  if (value === undefined) {
    return undefined;
  }
  if (typeof value !== "string" || value.length > maximum) {
    throw new Error(`${path} must be a bounded string`);
  }
  return value;
}

function optionalNumber(value: unknown, path: string): number | undefined {
  if (value === undefined) {
    return undefined;
  }
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new Error(`${path} must be a finite number`);
  }
  return value;
}

function optionalInteger(value: unknown, path: string): number | undefined {
  const number = optionalNumber(value, path);
  if (number !== undefined && (!Number.isInteger(number) || number < 0 || number > 4096)) {
    throw new Error(`${path} must be a bounded non-negative integer`);
  }
  return number;
}
