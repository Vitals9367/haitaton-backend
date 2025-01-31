# Needs maintenance VPN to access the fileshare.
start=`date +%s`
echo Importing tormays_buses_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_buses_polys.gpkg
echo tormays_buses_polys.gpkg imported OK.
echo Importing tormays_central_business_area_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_central_business_area_polys.gpkg
echo tormays_central_business_area_polys.gpkg imported OK
echo Importing tormays_critical_area_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_critical_area_polys.gpkg
echo tormays_critical_area_polys.gpkg imported OK.
echo Importing tormays_cycleways_basic_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_cycleways_basic_polys.gpkg
echo tormays_cycleways_basic_polys.gpkg imported OK.
echo Importing tormays_cycleways_main_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_cycleways_main_polys.gpkg
echo tormays_cycleways_main_polys.gpkg imported OK.
echo Importing tormays_cycleways_priority_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_cycleways_priority_polys.gpkg
echo tormays_cycleways_priority_polys.gpkg imported OK.
echo Importing tormays_street_classes_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_street_classes_polys.gpkg
echo tormays_street_classes_polys.gpkg imported OK.
echo Importing tormays_trams_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_trams_polys.gpkg
echo tormays_trams_polys.gpkg imported OK.
echo Importing tormays_volumes15_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_volumes15_polys.gpkg
echo tormays_volumes15_polys.gpkg imported OK.
echo Importing tormays_volumes30_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_volumes30_polys.gpkg
echo tormays_volumes30_polys.gpkg imported OK.
echo Importing tormays_ylre_classes_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_ylre_classes_polys.gpkg
echo tormays_ylre_classes_polys.gpkg imported OK.
echo Importing tormays_ylre_parts_polys.gpkg...
docker run --rm --network=haitaton-backend_backbone osgeo/gdal:alpine-small-latest ogr2ogr -overwrite -f PostgreSQL PG:"dbname='haitaton' host='db' port='5432' user='haitaton_user' password='haitaton'" http://haitaton-fileshare-dev.internal.apps.arodevtest.hel.fi/tormays_ylre_parts_polys.gpkg
echo tormays_ylre_parts_polys.gpkg imported OK.
end=`date +%s`
runtime=$((end-start))
echo Import took $((runtime / 60)) minutes
