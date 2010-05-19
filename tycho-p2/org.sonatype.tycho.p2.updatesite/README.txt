The P2 Publisher UpdateSite does not create a JREAction even if the bundles do require it. 
This ends up preventing us from installing an update site in a blank target platform:

The work around consists of extending the publisher app and inserting the required IU ourselves.