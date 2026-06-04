@echo off
echo ================================================
echo  Real-Time Orders Service
echo  Starting with embedded PostgreSQL...
echo  (first run downloads ~50 MB of PG binaries)
echo ================================================
echo.
echo  Once started:
echo    Backend API : http://localhost:8080/api/orders
echo    Dashboard   : open client\index.html in browser
echo.
./mvnw.cmd spring-boot:run
pause
