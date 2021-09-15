#!/bin/bash

AUTOXML=src/main/res/values/auto.xml
BRANCH=`git rev-parse --abbrev-ref HEAD`
if [ $? -ne 0 ]
then
  echo "*** Couldn't figure out the git branch I'm on, auto.xml not updated!"
  exit 1
fi

echo '<?xml version="1.0" encoding="utf-8"?>' > $AUTOXML
echo '<resources>' >> $AUTOXML
echo -n '  <string name="et_php_url">' >> $AUTOXML
if [ $BRANCH = 'devel' ]
then
  echo -n 'http://littlesvr.ca/et-devel/et.php' >> $AUTOXML
else
  echo -n 'http://littlesvr.ca/et/et.php' >> $AUTOXML
fi
echo '</string>' >> $AUTOXML
echo '</resources>' >> $AUTOXML

