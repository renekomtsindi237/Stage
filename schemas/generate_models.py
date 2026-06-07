#!/usr/bin/env python3
"""
IMF Pipeline — Génération de modèles depuis les schémas Avro
=============================================================

Génère automatiquement :
  - Python : modèles Pydantic v2 (→ schemas/generated/python/)
  - Java   : indications pour avro-maven-plugin (pom.xml)

Usage :
  python schemas/generate_models.py

Les modèles générés sont la source de vérité pour :
  - FastAPI (Python) — pipeline/api_ml.py, pipeline/kafka/
  - Spring Boot (Java) — backend/src/.../kafka/
  - Airflow DAGs — pipeline/dags/
"""

from __future__ import annotations

import json
import re
from datetime import datetime
from pathlib import Path
from textwrap import dedent
from typing import Any

AVRO_DIR   = Path(__file__).parent / "avro"
PYTHON_DIR = Path(__file__).parent / "generated" / "python"
PYTHON_DIR.mkdir(parents=True, exist_ok=True)

# Mapping type Avro → type Python/Pydantic
AVRO_TO_PYTHON: dict[str, str] = {
    "null":    "None",
    "boolean": "bool",
    "int":     "int",
    "long":    "int",
    "float":   "float",
    "double":  "float",
    "string":  "str",
    "bytes":   "bytes",
    "array":   "list",
}

# Logical types Avro → types Python
LOGICAL_TYPES: dict[str, str] = {
    "timestamp-millis": "datetime",
    "timestamp-micros": "datetime",
    "date":             "date",
    "time-millis":      "time",
    "uuid":             "UUID",
}


def avro_type_to_python(avro_type: Any) -> tuple[str, list[str]]:
    """Convertit un type Avro en type Python + imports nécessaires."""
    imports: list[str] = []

    if isinstance(avro_type, str):
        py = AVRO_TO_PYTHON.get(avro_type, avro_type)
        return py, imports

    if isinstance(avro_type, dict):
        logical = avro_type.get("logicalType")
        if logical in LOGICAL_TYPES:
            py = LOGICAL_TYPES[logical]
            if py == "datetime":  imports.append("from datetime import datetime")
            if py == "date":      imports.append("from datetime import date")
            if py == "time":      imports.append("from datetime import time")
            if py == "UUID":      imports.append("from uuid import UUID")
            return py, imports

        if avro_type["type"] == "enum":
            return avro_type["name"], imports

        if avro_type["type"] == "array":
            item_py, item_imports = avro_type_to_python(avro_type["items"])
            imports.extend(item_imports)
            return f"list[{item_py}]", imports

        if avro_type["type"] == "record":
            return avro_type["name"], imports

        return AVRO_TO_PYTHON.get(avro_type["type"], "Any"), imports

    if isinstance(avro_type, list):
        # Union type (nullable = ["null", "type"] → Optional[type])
        non_null = [t for t in avro_type if t != "null"]
        if len(non_null) == 1:
            py, sub_imports = avro_type_to_python(non_null[0])
            imports.extend(sub_imports)
            return f"Optional[{py}]", imports
        # Multi-union
        parts = []
        for t in non_null:
            py, sub_imports = avro_type_to_python(t)
            imports.extend(sub_imports)
            parts.append(py)
        return f"Union[{', '.join(parts)}]", imports

    return "Any", imports


def avro_default_to_python(default: Any, py_type: str) -> str:
    """Convertit une valeur par défaut Avro en littéral Python."""
    if default is None:
        return "None"
    if isinstance(default, bool):
        return "True" if default else "False"
    if isinstance(default, str):
        return f'"{default}"'
    if isinstance(default, list):
        return "[]"
    return str(default)


def generate_enum(enum_schema: dict) -> str:
    """Génère une classe Python Enum depuis un schéma Avro enum."""
    name    = enum_schema["name"]
    symbols = enum_schema["symbols"]
    lines   = [f'class {name}(str, Enum):']
    for s in symbols:
        lines.append(f'    {s} = "{s}"')
    return "\n".join(lines)


def extract_enums(schema: dict) -> list[dict]:
    """Extrait tous les types enum imbriqués dans un schéma record."""
    enums = []
    for field in schema.get("fields", []):
        ftype = field["type"]
        if isinstance(ftype, dict) and ftype.get("type") == "enum":
            enums.append(ftype)
        elif isinstance(ftype, list):
            for t in ftype:
                if isinstance(t, dict) and t.get("type") == "enum":
                    enums.append(t)
    return enums


def generate_pydantic_model(schema: dict) -> str:
    """Génère le code complet du modèle Pydantic depuis un schéma Avro record."""
    class_name = schema["name"]
    namespace  = schema.get("namespace", "")
    doc        = schema.get("doc", "")
    fields     = schema.get("fields", [])

    all_imports: set[str] = {
        "from __future__ import annotations",
        "from enum import Enum",
        "from typing import Optional, Union, Any",
        "from pydantic import BaseModel, Field",
    }

    enum_defs   = []
    field_lines = []

    for field in fields:
        fname    = field["name"]
        ftype    = field["type"]
        fdoc     = field.get("doc", "")
        fdefault = field.get("default", "__NODEFAULT__")

        # Traiter les énums imbriqués
        if isinstance(ftype, dict) and ftype.get("type") == "enum":
            enum_defs.append(generate_enum(ftype))
            py_type = ftype["name"]
            sub_imports = []
        elif isinstance(ftype, list):
            non_null_types = [t for t in ftype if t != "null"]
            has_enum = any(isinstance(t, dict) and t.get("type") == "enum" for t in non_null_types)
            if has_enum:
                for t in non_null_types:
                    if isinstance(t, dict) and t.get("type") == "enum":
                        enum_defs.append(generate_enum(t))
            py_type, sub_imports = avro_type_to_python(ftype)
            all_imports.update(sub_imports)
        else:
            py_type, sub_imports = avro_type_to_python(ftype)
            all_imports.update(sub_imports)

        # Construction de la ligne de champ
        if fdefault == "__NODEFAULT__":
            if fdoc:
                field_lines.append(f'    {fname}: {py_type} = Field(..., description="{fdoc}")')
            else:
                field_lines.append(f'    {fname}: {py_type}')
        else:
            py_def = avro_default_to_python(fdefault, py_type)
            if fdoc:
                field_lines.append(f'    {fname}: {py_type} = Field({py_def}, description="{fdoc}")')
            else:
                field_lines.append(f'    {fname}: {py_type} = {py_def}')

    # Assemblage
    imports_block = "\n".join(sorted(all_imports))
    enums_block   = "\n\n".join(enum_defs)

    model_doc = f'    """{doc}"""' if doc else ""
    model_body = "\n".join(field_lines)

    code = dedent(f"""\
    {imports_block}

    # ─────────────────────────────────────────────────────────────────────────────
    # Auto-généré depuis schemas/avro/{class_name}.avsc
    # Namespace Avro : {namespace}
    # Ne pas modifier manuellement — relancer generate_models.py
    # Généré le : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
    # ─────────────────────────────────────────────────────────────────────────────
    """)

    if enums_block:
        code += f"\n\n{enums_block}\n"

    code += dedent(f"""

    class {class_name}(BaseModel):
    {model_doc}
    {model_body}

        class Config:
            use_enum_values = True
            json_encoders = {{
                "datetime": lambda v: int(v.timestamp() * 1000),
            }}
    """)
    return code


def main() -> None:
    avsc_files = sorted(AVRO_DIR.glob("*.avsc"))
    if not avsc_files:
        print(f"Aucun fichier .avsc trouvé dans {AVRO_DIR}")
        return

    init_imports = []

    for avsc_path in avsc_files:
        schema = json.loads(avsc_path.read_text(encoding="utf-8"))
        if schema.get("type") != "record":
            print(f"  Ignoré (non-record) : {avsc_path.name}")
            continue

        class_name = schema["name"]
        code       = generate_pydantic_model(schema)

        out_path = PYTHON_DIR / f"{class_name}.py"
        out_path.write_text(code, encoding="utf-8")
        print(f"  OK {class_name:25s} -> {out_path.relative_to(Path.cwd()) if Path.cwd() in out_path.parents else out_path}")
        init_imports.append(class_name)

    # Génération du __init__.py
    init_lines = [
        '"""Auto-généré depuis les schémas Avro — ne pas modifier manuellement."""',
        "",
    ]
    for name in init_imports:
        init_lines.append(f"from .{name} import {name}  # noqa: F401")
    init_lines.append("")
    init_lines.append(f"__all__ = {init_imports!r}")

    (PYTHON_DIR / "__init__.py").write_text("\n".join(init_lines), encoding="utf-8")

    print(f"\n{len(init_imports)} modèles Pydantic générés dans {PYTHON_DIR}")
    print("\nInstructions Java (avro-maven-plugin dans pom.xml) :")
    print("  Les classes Java sont générées automatiquement par : mvn generate-sources")
    print("  Voir : backend/pom.xml -> plugin avro-maven-plugin")


if __name__ == "__main__":
    main()
