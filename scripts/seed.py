#!/usr/bin/env python3
"""Download and import the public MacroSaurus food catalogs directly into PostgreSQL."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
import time
import urllib.request
import uuid
import zipfile
from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parent.parent
DEFAULT_ENV_FILE = ROOT / ".env.production"
USER_AGENT = "MacroSaurus catalog importer"
BATCH_SIZE = 1_000

DEFAULTS = {
    "USDA_FOUNDATION_RELEASE": "2026-04",
    "USDA_FOUNDATION_URL": "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_json_2026-04-30.zip",
    "USDA_SR_LEGACY_RELEASE": "2018-04",
    "USDA_SR_LEGACY_URL": "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_json_2018-04.zip",
    "MATVARETABELLEN_RELEASE": "2026",
    "MATVARETABELLEN_EN_URL": "https://www.matvaretabellen.no/api/en/foods.json",
    "MATVARETABELLEN_NB_URL": "https://www.matvaretabellen.no/api/nb/foods.json",
}

MATVARE_NUTRIENTS = {
    "Fett": ("fat_g", "g"),
    "Mettet": ("saturated_fat_g", "g"),
    "Trans": ("trans_fat_g", "g"),
    "Enumet": ("monounsaturated_fat_g", "g"),
    "Flerum": ("polyunsaturated_fat_g", "g"),
    "Kolest": ("cholesterol_mg", "mg"),
    "Karbo": ("carbohydrate_g", "g"),
    "Mono+Di": ("sugars_g", "g"),
    "Fiber": ("fiber_g", "g"),
    "Protein": ("protein_g", "g"),
    "Na": ("sodium_mg", "mg"),
    "Ca": ("calcium_mg", "mg"),
    "Fe": ("iron_mg", "mg"),
    "K": ("potassium_mg", "mg"),
    "Vit C": ("vitamin_c_mg", "mg"),
    "Vit D": ("vitamin_d_ug", "ug"),
    "Vit A": ("vitamin_a_ug", "ug"),
    "Vit E": ("vitamin_e_mg", "mg"),
    "VITK1": ("vitamin_k_ug", "ug"),
    "Vit B1": ("thiamin_mg", "mg"),
    "Vit B2": ("riboflavin_mg", "mg"),
    "Niacin": ("niacin_mg", "mg"),
    "Vit B6": ("vitamin_b6_mg", "mg"),
    "Folat": ("folate_ug", "ug"),
    "Vit B12": ("vitamin_b12_ug", "ug"),
    "Mg": ("magnesium_mg", "mg"),
    "P": ("phosphorus_mg", "mg"),
    "Zn": ("zinc_mg", "mg"),
    "Cu": ("copper_mg", "mg"),
    "Mn": ("manganese_mg", "mg"),
    "Se": ("selenium_ug", "ug"),
}

USDA_NUTRIENTS = (
    ("energy_kcal", "kcal", (1008, 2047, 2048), re.compile(r"^energy")),
    ("protein_g", "g", (1003,), re.compile(r"^protein$")),
    ("carbohydrate_g", "g", (1005,), re.compile(r"^carbohydrate")),
    ("fat_g", "g", (1004,), re.compile(r"total lipid|total fat")),
    ("fiber_g", "g", (1079,), re.compile(r"fiber,? total dietary")),
    ("sugars_g", "g", (2000, 1063), re.compile(r"sugars?,? total")),
    ("saturated_fat_g", "g", (1258,), re.compile(r"fatty acids,? total saturated")),
    ("trans_fat_g", "g", (1257,), re.compile(r"fatty acids,? total trans")),
    ("monounsaturated_fat_g", "g", (1292,), re.compile(r"fatty acids,? total monounsaturated")),
    ("polyunsaturated_fat_g", "g", (1293,), re.compile(r"fatty acids,? total polyunsaturated")),
    ("cholesterol_mg", "mg", (1253,), re.compile(r"^cholesterol$")),
    ("sodium_mg", "mg", (1093,), re.compile(r"^sodium")),
    ("calcium_mg", "mg", (1087,), re.compile(r"^calcium")),
    ("iron_mg", "mg", (1089,), re.compile(r"^iron")),
    ("potassium_mg", "mg", (1092,), re.compile(r"^potassium")),
    ("vitamin_c_mg", "mg", (1162,), re.compile(r"vitamin c")),
    ("vitamin_d_ug", "ug", (1114,), re.compile(r"vitamin d.*(?:d2 \+ d3|total)")),
    ("vitamin_a_ug", "ug", (1106,), re.compile(r"vitamin a,? rae")),
    ("vitamin_e_mg", "mg", (1109,), re.compile(r"vitamin e.*alpha")),
    ("vitamin_k_ug", "ug", (1185,), re.compile(r"vitamin k.*phylloquinone")),
    ("thiamin_mg", "mg", (1165,), re.compile(r"^thiamin")),
    ("riboflavin_mg", "mg", (1166,), re.compile(r"^riboflavin")),
    ("niacin_mg", "mg", (1167,), re.compile(r"^niacin")),
    ("vitamin_b6_mg", "mg", (1175,), re.compile(r"vitamin b-?6")),
    ("folate_ug", "ug", (1177,), re.compile(r"folate,? total")),
    ("vitamin_b12_ug", "ug", (1178,), re.compile(r"vitamin b-?12")),
    ("magnesium_mg", "mg", (1090,), re.compile(r"^magnesium")),
    ("phosphorus_mg", "mg", (1091,), re.compile(r"^phosphorus")),
    ("zinc_mg", "mg", (1095,), re.compile(r"^zinc")),
    ("copper_mg", "mg", (1098,), re.compile(r"^copper")),
    ("manganese_mg", "mg", (1101,), re.compile(r"^manganese")),
    ("selenium_ug", "ug", (1103,), re.compile(r"^selenium")),
)


@dataclass
class Portion:
    name: str
    gram_weight: Decimal
    default: bool = False


@dataclass
class Food:
    external_id: str
    name: str
    locale: str = "en"
    aliases: dict[str, str] = field(default_factory=dict)
    nutrients: dict[str, Decimal] = field(default_factory=dict)
    portions: list[Portion] = field(default_factory=list)


@dataclass
class Release:
    source: str
    key: str
    checksum: str
    foods: list[Food]


def clean_text(value: Any) -> str | None:
    cleaned = re.sub(r"\s+", " ", str(value or "")).strip()
    return cleaned or None


def finite_number(value: Any) -> Decimal | None:
    if value is None or value == "" or isinstance(value, bool):
        return None
    try:
        number = Decimal(str(value))
    except InvalidOperation:
        return None
    return number if number.is_finite() and number >= 0 else None


def convert_amount(value: Any, from_unit: Any, to_unit: str) -> Decimal | None:
    amount = finite_number(value)
    if amount is None:
        return None
    source = str(from_unit or "").strip().lower().replace("µ", "u").replace("μ", "u").replace("-re", "")
    target = to_unit.lower()
    if source == target:
        return amount
    grams = {"g": Decimal(1), "mg": Decimal("0.001"), "ug": Decimal("0.000001")}
    if source not in grams or target not in grams:
        return None
    return amount * grams[source] / grams[target]


def default_portions(portions: Iterable[Portion | None]) -> list[Portion]:
    result = [portion for portion in portions if portion is not None]
    for index, portion in enumerate(result):
        portion.default = index == 0
    return result


def matvare_portion(raw: dict[str, Any]) -> Portion | None:
    grams = convert_amount(raw.get("quantity"), raw.get("unit"), "g")
    name = clean_text(raw.get("portionName")) or clean_text(raw.get("portionUnit"))
    return Portion(name, grams) if name and grams is not None and grams > 0 else None


def matvare_nutrients(food: dict[str, Any]) -> dict[str, Decimal]:
    nutrients: dict[str, Decimal] = {}
    calories = food.get("calories") or {}
    amount = convert_amount(calories.get("quantity"), calories.get("unit"), "kcal")
    if amount is not None:
        nutrients["energy_kcal"] = amount
    constituents = food.get("constituents") if isinstance(food.get("constituents"), list) else []
    for constituent in constituents:
        mapping = MATVARE_NUTRIENTS.get(constituent.get("nutrientId"))
        if not mapping:
            continue
        code, unit = mapping
        amount = convert_amount(constituent.get("quantity"), constituent.get("unit"), unit)
        if amount is not None:
            nutrients[code] = amount
    if "sodium_mg" not in nutrients:
        salt = next((item for item in constituents if item.get("nutrientId") == "NaCl"), None)
        if salt:
            salt_grams = convert_amount(salt.get("quantity"), salt.get("unit"), "g")
            if salt_grams is not None:
                nutrients["sodium_mg"] = salt_grams * 400
    return nutrients


def prepare_matvare(english: dict[str, Any], norwegian: dict[str, Any], key: str, checksum: str) -> Release:
    english_foods = english.get("foods")
    norwegian_foods = norwegian.get("foods")
    if not isinstance(english_foods, list) or not isinstance(norwegian_foods, list):
        raise ValueError("Matvaretabellen exports must contain a foods array")
    norwegian_names = {
        str(food.get("foodId")): clean_text(food.get("foodName")) for food in norwegian_foods if isinstance(food, dict)
    }
    foods = []
    for raw in english_foods:
        external_id = clean_text(raw.get("foodId"))
        name = clean_text(raw.get("foodName"))
        if not external_id or not name:
            raise ValueError("Matvaretabellen food is missing foodId or foodName")
        norwegian_name = norwegian_names.get(external_id)
        portions = raw.get("portions") if isinstance(raw.get("portions"), list) else []
        foods.append(
            Food(
                external_id,
                name,
                aliases={"nb": norwegian_name} if norwegian_name and norwegian_name != name else {},
                nutrients=matvare_nutrients(raw),
                portions=default_portions(matvare_portion(item) for item in portions),
            )
        )
    return Release("MATVARETABELLEN", key, checksum, foods)


def usda_nutrient_details(item: dict[str, Any]) -> tuple[int | None, str, Any, Any]:
    nutrient = item.get("nutrient") or {}
    raw_id = nutrient.get("id", item.get("nutrientId"))
    try:
        nutrient_id = int(raw_id)
    except (TypeError, ValueError):
        nutrient_id = None
    name = str(nutrient.get("name", item.get("nutrientName", ""))).strip().lower()
    return nutrient_id, name, nutrient.get("unitName", item.get("unitName")), item.get("amount", item.get("value"))


def usda_nutrients(food: dict[str, Any]) -> dict[str, Decimal]:
    raw = food.get("foodNutrients") if isinstance(food.get("foodNutrients"), list) else []
    available = [usda_nutrient_details(item) for item in raw]
    by_id = {item[0]: item for item in available if item[0] is not None}
    result: dict[str, Decimal] = {}
    for code, unit, ids, name_pattern in USDA_NUTRIENTS:
        match = next((by_id[nutrient_id] for nutrient_id in ids if nutrient_id in by_id), None)
        if match is None:
            match = next((item for item in available if name_pattern.search(item[1])), None)
        if match is not None:
            amount = convert_amount(match[3], match[2], unit)
            if amount is not None:
                result[code] = amount
    return result


def usda_portion(raw: dict[str, Any]) -> Portion | None:
    grams = finite_number(raw.get("gramWeight"))
    if grams is None or grams <= 0:
        return None
    amount = finite_number(raw.get("amount")) or Decimal(1)
    measure = raw.get("measureUnit") or {}
    descriptor = (
        clean_text(raw.get("portionDescription"))
        or clean_text(raw.get("modifier"))
        or clean_text(measure.get("abbreviation"))
        or clean_text(measure.get("name"))
        or "serving"
    )
    prefix = str(amount.normalize()) if amount != 1 else "1"
    name = descriptor if descriptor.startswith(prefix) else f"{prefix} {descriptor}"
    return Portion(name, grams)


def prepare_usda(document: dict[str, Any], source: str, key: str, checksum: str) -> Release:
    root = {"USDA_FOUNDATION": "FoundationFoods", "USDA_SR_LEGACY": "SRLegacyFoods"}[source]
    raw_foods = document.get(root)
    if not isinstance(raw_foods, list):
        raise ValueError(f"USDA {source} export must contain {root}")
    foods = []
    for raw in (food for food in raw_foods if food is not None):
        external_id = clean_text(raw.get("fdcId"))
        name = clean_text(raw.get("description"))
        if not external_id or not name:
            raise ValueError(f"USDA {source} food is missing fdcId or description")
        portions = raw.get("foodPortions") if isinstance(raw.get("foodPortions"), list) else []
        foods.append(
            Food(
                external_id,
                name,
                nutrients=usda_nutrients(raw),
                portions=default_portions(usda_portion(item) for item in portions),
            )
        )
    return Release(source, key, checksum, foods)


def load_env_file(path: Path) -> None:
    if not path.exists():
        return
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            raise ValueError(f"Invalid environment line {line_number} in {path}")
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        os.environ.setdefault(key, value)


def setting(name: str) -> str:
    return os.environ.get(name) or DEFAULTS.get(name, "")


def download(url: str, destination: Path) -> None:
    print(f"Downloading {url}...", flush=True)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(request, timeout=120) as response, destination.open("wb") as output:
                shutil.copyfileobj(response, output, length=1024 * 1024)
            print(f"  downloaded {destination.stat().st_size / 1024 / 1024:.1f} MB", flush=True)
            return
        except Exception:
            if attempt == 3:
                raise
            time.sleep(2)


def checksum_files(*paths: Path) -> str:
    digest = hashlib.sha256()
    for path in paths:
        digest.update(str(path.stat().st_size).encode())
        digest.update(b":")
        with path.open("rb") as source:
            while chunk := source.read(1024 * 1024):
                digest.update(chunk)
    return f"sha256:{digest.hexdigest()}"


def read_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8-sig") as source:
        return json.load(source, parse_float=Decimal)


def read_usda_archive(path: Path, expected_root: str) -> tuple[dict[str, Any], str]:
    with zipfile.ZipFile(path) as archive:
        members = [item for item in archive.infolist() if not item.is_dir() and item.filename.lower().endswith(".json")]
        if len(members) != 1:
            raise ValueError(f"Expected one JSON file in {path.name}, found {len(members)}")
        member = members[0]
        digest = hashlib.sha256()
        digest.update(str(member.file_size).encode())
        digest.update(b":")
        with archive.open(member) as source:
            while chunk := source.read(1024 * 1024):
                digest.update(chunk)
        with archive.open(member) as source:
            document = json.load(source, parse_float=Decimal)
    if expected_root not in document:
        raise ValueError(f"USDA archive must contain {expected_root}")
    return document, f"sha256:{digest.hexdigest()}"


def batches(rows: list[tuple[Any, ...]]) -> Iterable[list[tuple[Any, ...]]]:
    for start in range(0, len(rows), BATCH_SIZE):
        yield rows[start : start + BATCH_SIZE]


def execute_values(
    cursor: Any,
    prefix: str,
    row_template: str,
    rows: list[tuple[Any, ...]],
    suffix: str = "",
) -> None:
    for batch in batches(rows):
        placeholders = ",".join(row_template for _ in batch)
        parameters = [value for row in batch for value in row]
        cursor.execute(f"{prefix} {placeholders} {suffix}", parameters)


def import_release(connection: Any, release: Release) -> None:
    if not release.foods:
        raise ValueError(f"{release.source} release contains no foods")
    external_ids = [food.external_id for food in release.foods]
    if len(external_ids) != len(set(external_ids)):
        raise ValueError(f"{release.source} release contains duplicate external IDs")

    print(f"Importing {len(release.foods)} {release.source} foods...", flush=True)
    try:
        with connection.cursor() as cursor:
            cursor.execute("select pg_advisory_xact_lock(hashtext(%s))", (f"macrosaurus-catalog-{release.source}",))
            cursor.execute(
                "select status, record_count from food_source_releases "
                "where source_kind = %s and release_key = %s and checksum = %s",
                (release.source, release.key, release.checksum),
            )
            previous = cursor.fetchone()
            if previous and previous[0] == "COMPLETED":
                print(f"  already imported ({previous[1]} foods); skipping", flush=True)
                return

            cursor.execute("select code from nutrient_definitions")
            known_nutrients = {row[0] for row in cursor.fetchall()}
            used_nutrients = {code for food in release.foods for code in food.nutrients}
            unknown = sorted(used_nutrients - known_nutrients)
            if unknown:
                raise ValueError(f"Database does not define nutrient codes: {', '.join(unknown)}")

            cursor.execute(
                "select f.external_id, f.id::text, coalesce(max(fr.revision), 0) "
                "from foods f left join food_revisions fr on fr.food_id = f.id "
                "where f.source_kind = %s group by f.id, f.external_id",
                (release.source,),
            )
            existing = {row[0]: (row[1], row[2]) for row in cursor.fetchall()}
            release_id = str(uuid.uuid4())
            prepared = []
            for food in release.foods:
                current = existing.get(food.external_id)
                prepared.append(
                    (food, current[0] if current else str(uuid.uuid4()), str(uuid.uuid4()), (current[1] if current else 0) + 1, current is not None)
                )

            cursor.execute(
                "insert into food_source_releases(id, source_kind, release_key, checksum, status) "
                "values (%s, %s, %s, %s, 'IMPORTING')",
                (release_id, release.source, release.key, release.checksum),
            )
            cursor.execute("update foods set active = false where source_kind = %s", (release.source,))

            new_food_rows = [
                (food_id, release.source, food.external_id)
                for food, food_id, _, _, exists in prepared
                if not exists
            ]
            existing_food_rows = [(food_id,) for _, food_id, _, _, exists in prepared if exists]
            print(
                f"  writing {len(new_food_rows)} new and {len(existing_food_rows)} existing foods...",
                flush=True,
            )
            execute_values(
                cursor,
                "insert into foods(id, source_kind, external_id, barcode, active) values",
                "(%s, %s, %s, null, true)",
                new_food_rows,
            )
            execute_values(
                cursor,
                "update foods as food set barcode = null, active = true from (values",
                "(%s::uuid)",
                existing_food_rows,
                ") as changed(id) where food.id = changed.id",
            )
            revision_rows = [
                (revision_id, food_id, revision, food.name, release_id, food.locale)
                for food, food_id, revision_id, revision, _ in prepared
            ]
            print(f"  writing {len(revision_rows)} revisions...", flush=True)
            execute_values(
                cursor,
                "insert into food_revisions(id, food_id, revision, name, brand, basis_type, basis_amount, "
                "basis_unit, density_g_per_ml, source_release_id, locale) values",
                "(%s, %s, %s, %s, null, 'PER_100_G', 100, 'g', null, %s, %s)",
                revision_rows,
            )
            nutrient_rows = [
                (revision_id, code, amount)
                for food, _, revision_id, _, _ in prepared
                for code, amount in food.nutrients.items()
            ]
            print(f"  writing {len(nutrient_rows)} nutrient values...", flush=True)
            execute_values(
                cursor,
                "insert into food_nutrients(food_revision_id, nutrient_code, amount, value_kind) values",
                "(%s, %s, %s, 'REPORTED')",
                nutrient_rows,
            )
            portion_rows = [
                (str(uuid.uuid4()), revision_id, portion.name, portion.gram_weight, portion.default)
                for food, _, revision_id, _, _ in prepared
                for portion in food.portions
            ]
            print(f"  writing {len(portion_rows)} portions...", flush=True)
            execute_values(
                cursor,
                "insert into portions(id, food_revision_id, name, quantity, gram_weight, is_default) values",
                "(%s, %s, %s, 1, %s, %s)",
                portion_rows,
            )
            cursor.execute(
                "delete from food_aliases where food_id in (select id from foods where source_kind = %s)",
                (release.source,),
            )
            alias_rows = [
                (food_id, locale.strip(), name.strip())
                for food, food_id, _, _, _ in prepared
                for locale, name in food.aliases.items()
                if locale.strip() and name.strip()
            ]
            print(f"  writing {len(alias_rows)} aliases...", flush=True)
            execute_values(
                cursor,
                "insert into food_aliases(food_id, locale, name) values",
                "(%s, %s, %s)",
                alias_rows,
                "on conflict do nothing",
            )
            cursor.execute(
                "update food_source_releases set status = 'COMPLETED', record_count = %s, "
                "imported_at = current_timestamp where id = %s",
                (len(release.foods), release_id),
            )
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    print(
        f"  complete: {len(release.foods)} foods, {len(nutrient_rows)} nutrients, "
        f"{len(portion_rows)} portions, {len(alias_rows)} aliases",
        flush=True,
    )


def database_driver() -> Any:
    try:
        import psycopg

        return psycopg
    except ImportError:
        try:
            import psycopg2

            return psycopg2
        except ImportError as error:
            raise RuntimeError(
                "PostgreSQL Python driver missing. On Ubuntu run: sudo apt install python3-psycopg\n"
                "Alternatively install it in a virtualenv: pip install 'psycopg[binary]'"
            ) from error


def validate_database_settings() -> None:
    required = ("DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD")
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise ValueError(f"Missing database settings: {', '.join(missing)}")


def connect_database(driver: Any) -> Any:
    url = os.environ["DATABASE_URL"]
    if url.startswith("jdbc:"):
        url = url[5:]
    return driver.connect(
        url,
        user=os.environ["DATABASE_USERNAME"],
        password=os.environ["DATABASE_PASSWORD"],
    )


def import_once(driver: Any, release: Release) -> None:
    connection = connect_database(driver)
    try:
        import_release(connection, release)
    finally:
        connection.close()


def matvare_release(work: Path) -> Release:
    english_path, norwegian_path = work / "matvare-en.json", work / "matvare-nb.json"
    download(setting("MATVARETABELLEN_EN_URL"), english_path)
    download(setting("MATVARETABELLEN_NB_URL"), norwegian_path)
    checksum = checksum_files(english_path, norwegian_path)
    norwegian = read_json(norwegian_path)
    english = read_json(english_path)
    return prepare_matvare(english, norwegian, setting("MATVARETABELLEN_RELEASE"), checksum)


def usda_release(work: Path, source: str) -> Release:
    prefix = "USDA_FOUNDATION" if source == "USDA_FOUNDATION" else "USDA_SR_LEGACY"
    root = "FoundationFoods" if source == "USDA_FOUNDATION" else "SRLegacyFoods"
    archive_path = work / f"{prefix.lower()}.zip"
    download(setting(f"{prefix}_URL"), archive_path)
    document, checksum = read_usda_archive(archive_path, root)
    return prepare_usda(document, source, setting(f"{prefix}_RELEASE"), checksum)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", choices=("usda", "matvaretabellen", "both"), default="both")
    parser.add_argument(
        "--env-file",
        type=Path,
        default=Path(os.environ.get("DEPLOY_ENV_FILE", DEFAULT_ENV_FILE)),
        help="environment file (default: .env.production)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    load_env_file(args.env_file)
    validate_database_settings()
    driver = database_driver()
    with tempfile.TemporaryDirectory(prefix="macrosaurus-seed-") as temporary:
        work = Path(temporary)
        if args.source in ("usda", "both"):
            import_once(driver, usda_release(work, "USDA_FOUNDATION"))
            import_once(driver, usda_release(work, "USDA_SR_LEGACY"))
        if args.source in ("matvaretabellen", "both"):
            import_once(driver, matvare_release(work))
    print("Catalog seeding completed successfully.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\nCancelled; the current release transaction was rolled back.", file=sys.stderr)
        raise SystemExit(130)
    except Exception as error:
        print(f"Seed failed: {error}", file=sys.stderr)
        raise SystemExit(1)
