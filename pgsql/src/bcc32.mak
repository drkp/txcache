# $PostgreSQL: pgsql/src/bcc32.mak,v 1.4 2006/10/06 18:53:53 tgl Exp $

# Makefile for Borland C++ 5.5 (or compat)
# Top-file makefile for Win32 parts of postgresql.
# Note that most parts are not ported to Win32!

!IF "$(CFG)" != "Release" && "$(CFG)" != "Debug"
!MESSAGE Invalid configuration "$(CFG)" specified.
!MESSAGE You can specify a configuration when running MAKE
!MESSAGE by defining the macro CFG on the command line. For example:
!MESSAGE
!MESSAGE make  -DCFG=[Release | Debug] /f bcc32.mak
!MESSAGE
!MESSAGE Possible choices for configuration are:
!MESSAGE
!MESSAGE "Release" (Win32 Release )
!MESSAGE "Debug" (Win32 Debug)
!MESSAGE
!ENDIF

!IF "$(OS)" == "Windows_NT"
NULL=
!ELSE 
NULL=nul
!ENDIF 

ALL: 
   cd include
   if not exist pg_config.h copy pg_config.h.win32 pg_config.h
   if not exist pg_config_os.h copy port\win32.h pg_config_os.h
   cd ..
   cd interfaces\libpq
   make -N -DCFG=$(CFG) /f bcc32.mak 
   cd ..\..\bin\psql
   make -N -DCFG=$(CFG) /f bcc32.mak
   cd ..\..
   echo All Win32 parts have been built!

CLEAN:
   cd interfaces\libpq
   make -N -DCFG=Release /f bcc32.mak CLEAN
   make -N -DCFG=Debug /f bcc32.mak CLEAN
   cd ..\..\bin\psql
   make -N -DCFG=Release /f bcc32.mak CLEAN
   make -N -DCFG=Debug /f bcc32.mak CLEAN
   cd ..\..
   echo All Win32 parts have been cleaned!

DISTCLEAN: CLEAN
   cd include
   del pg_config.h pg_config_os.h
   cd ..
