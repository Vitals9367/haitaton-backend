# Intro

Download and process Haitaton spatial material (previously known as _laastariaineisto_).

# Quickstart

In order to get started, following steps need to be taken.

Actual details to be taken are described in following sections later in this document.

- build _fetch_ and _process_ images
- copy necessary script files to external volume. Volume is created during file copying process.
- run _fetch_ container
- optional: inspect external volume contents
- optional: run _processing_ container (skeleton only)
- optional: initialize volume for database
- optional: start database service

# Architecture, general description

Data is fetched from data sources and processed accordingly with a custom built containers.

## haitaton-gis-fetch

Downloads the requested data.

## haitaton-gis-process

Process data. Actual processing requirements are data dependent.

## database

Local development database is set up with PostGIS spatial support.

## Volume mappings

Local directory `data` is mapped to fetch container.

Local directory `haitaton-downloads` is mapped to fetch and processing containers.

Local directory `haitaton-gis-output` is mapped to processing container.

External volume `haitaton_gis_prepare` contains scripts for processing.

External volume `haitaton_gis_db` is dedicated for database.

## Volumes

TODO: verify that _haitaton_gis_prepare_ is created during file copy

External volumes are set up

- haitaton_gis_prepare
- haitaton_gis_db

Initialize volumes:

```
docker volume create --name=haitaton_gis_prepare
docker volume create --name=haitaton_gis_db
```

N.b. haitaton_gis_prepare volume is automatically generated during data copying script use.

Removal of external volumes (destructive):

```
docker volume rm haitaton_gis_prepare
docker volume rm haitaton_gis_db
```

Local directory bind mount is visible to data fetch and data processing containers:

- ./haitaton-downloads

### Inspect _haitaton_gis_prepare_ volume contents

Run:

```sh
sh inspect-disk.sh
```

Container is created, and volume contents can be found in directory: `/haitaton-gis`.

When done, leave shell with `exit` command.

## Build images

```
docker-compose build
```

Will build _fetch_ and _process_ images. Note, that extra step is needed
to copy actual script files to external volume.

## Copy script files to external volume

Script files are copied to external disk using:

```
sh copy-files.sh
```

After active development phase it might be more practical to handle file
copying in `Dockerfile`s and avoid explicit copying.

## Run data fetch

```
docker-compose run --rm gis-fetch <source_1> ... <source_N>
```

Where `<source>` is currently one of:

- `hsl` - HSL bus schedules
- `hki` - Helsinki area GIS material, polygon
- `osm` - OpenStreetMap export of Finland (with focus area clipping)
- `helsinki_osm_lines` - line geometry export from `osm`, covering area of city of Helsinki
- `ylre_katualueet` - Helsinki YLRE street areas, polygons.
- `ylre_katuosat` - Helsinki YLRE parts, polygons.
- `maka_autoliikennemaarat` - Traffic volumes (car traffic)
- `cycle_infra` - Cycle infra (local file)

Data files are downloaded to `./haitaton-downloads` -directory.

Primary function in data download is to download from remote location (and possibly convert) file during download.

Exceptions are listed below.

### `helsinki_osm_lines`

Prerequisites:

- `osm` material fetched
- `hki` material fetched

  Helsinki OSM line geometries are obtained by intersecting city of Helsinki area
  and OSM GIS material from whole Finland.

Materials are expected to be previously fetched to local directory.

Intersection is computed using OGR VRT driver in actual fetch operation.

## Run processing

Data processing interface is similar to data fetch.

```
docker-compose run --rm gis-process <source>
```

Currently supported processing targets are:

### `hsl`

Prerequisite: downloaded `hsl` and `hki` materials.

Docker example run (ensure that image build and file copying is
already performed as instructed above):

```sh
docker-compose up -d gis-db
docker-compose run --rm gis-fetch hsl hki
docker-compose run --rm gis-process hsl
docker-compose stop gis-db
```

Processed GIS material is available in:
haitaton-gis-output

Files (names configured in `config.yaml`)

- buses_lines.gpkg
- tormays_buses_polys.gpkg

### `maka_autoliikennemaarat`

Docker example run (ensure that image build and file copying is
already performed as instructed above):

```sh
docker-compose up -d gis-db
docker-compose run --rm gis-fetch maka_autoliikennemaarat
docker-compose run --rm gis-process maka_autoliikennemaarat
docker-compose stop gis-db
```

Processed GIS material is available in:
haitaton-gis-output

Files (names configured in `config.yaml`)

- volume_lines.gpkg
- tormays_volumes15_polys.gpkg
- tormays_volumes30_polys.gpkg

### `ylre_katuosat`

Docker example run (ensure that image build and file copying is
already performed as instructed above):

```sh
docker-compose up -d gis-db
docker-compose run --rm gis-fetch ylre_katuosat
docker-compose run --rm gis-process ylre_katuosat
docker-compose stop gis-db
```

Processed GIS material is available in:
haitaton-gis-output

Files (names configured in `config.yaml`)

- ylre_parts_orig_polys.gpkg
- tormays_ylre_parts_polys.gpkg

### `ylre_katualueet`

Docker example run (ensure that image build and file copying is
already performed as instructed above):

```sh
docker-compose up -d gis-db
docker-compose run --rm gis-fetch ylre_katualueet
docker-compose run --rm gis-process ylre_katualueet
docker-compose stop gis-db
```

Processed GIS material is available in:
haitaton-gis-output

Files (names configured in `config.yaml`)

- ylre_classes_orig_polys.gpkg
- tormays_ylre_classes_polys.gpkg

### `tram_infra`

Prerequisite: fetched `osm`, `hki` and `helsinki_osm_lines` -materials.

Docker example run (ensure that image build and file copying is
already performed as instructed above):

```sh
docker-compose up -d gis-db
docker-compose run --rm gis-fetch hki osm helsinki_osm_lines
docker-compose run --rm gis-process tram_infra
docker-compose stop gis-db
```

Processed GIS material is available in:
haitaton-gis-output

Output files (names configured in `config.yaml`)

- tram_infra.gpkg
- tormays_tram_infra_polys.gpkg

### `tram_lines`

Prerequisite: fetched `hsl` -material.

Docker example run (ensure that image build and file copying is
already performed as instructed above):

```sh
docker-compose up -d gis-db
docker-compose run --rm gis-fetch hsl
docker-compose run --rm gis-process tram_lines
docker-compose stop gis-db
```

Processed GIS material is available in:
haitaton-gis-output

Output files (names configured in `config.yaml`)

- tram_lines.gpkg
- tormays_tram_lines_polys.gpkg

### `cycle_infra`

Prerequisite: copy source file to `data` -directory. See `data/README.md`

Docker example run (ensure that image build and file copying is
already performed as instructed above):

```sh
docker-compose up -d gis-db
docker-compose run --rm gis-fetch cycle_infra
docker-compose run --rm gis-process cycle_infra
docker-compose stop gis-db
```

Processed GIS material is available in:
haitaton-gis-output

Output files (names configured in `config.yaml`)

- cycle_infra.gpkg
- tormays_cycle_infra_polys.gpkg

# Run tests

## Run all tests

Configure and activate python virtual environment.

Fetch all source data, as described above.

Run following in `gis-material-update/process` -directory.

```sh
[(venv)::process/]$ python -m unittest discover -v
```

## Run specific test

To run specific test (here: tram_lines)

```sh
[(venv)::process/]$ python -m unittest test/test_tram_lines.py
```

# Run processing in IDE

Main entrypoint for processing is `process_data.py`

It is safer to set virtual environment manually, and use that in IDE debugger.

Set environment variable `TORMAYS_DEPLOYMENT_PROFILE="local_development"`

# Maintenance process

In order to maintain current process, or support new materials, following
steps need to be taken.

## Adding new data source

Basic principle is the following:

- add relevant configuration section to configuration YAML file
- implement download support to data fetch script
- implement support in processing script
- build containers (if required)
- copy files to external volume
- run fetch and process

### Adding support to data fetch

- Edit data fetch script in `fetch` directory.
- Copy edited script to external volume
- run data fetch via `docker compose`
- verify that files are downloaded to shared directory

### Adding support to data processing

- edit processing script in `process` directory
- support processing of new data source
  - add new processing class
  - implement necessary functionality
  - add produced GIS material to database

# Miscellaneous instructions

## Creating python virtual environment

To create python environment:

```sh
# Python installation is required
[gis-material-update/process]$ python -m venv venv
[gis-material-update/process]$ source venv/bin/activate
[(venv) gis-material-update/process]$ pip install --upgrade pip
[(venv) gis-material-update/process]$ pip install -r requirements.txt
```

To exit from already activated virtual environment:

```
[(venv) gis-material-update/process]$ ./venv/bin/deactivate
```

To activate already created virtual environment:

```
[gis-material-update/process]$ ./venv/bin/activate
```

## Problems in virtual environment creation

External dependencies are required to install `pyjq`

Here are listed (some) required dependencies for Ubuntu 20.04.

- software-properties-common
- python-is-python3
- python3.10
- python3.10-venv
- python3.10-dev
- build-essential
- autoconf
- libtool
- git
