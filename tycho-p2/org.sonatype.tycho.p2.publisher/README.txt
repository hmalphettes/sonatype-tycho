The P2 Publisher UpdateSite does not create a JREAction even if the bundles do require it. 
This ends up preventing us from installing an update site in a blank target platform:
https://bugs.eclipse.org/bugs/show_bug.cgi?id=311795
The work around consists of extending the publisher app and inserting the required IU ourselves.