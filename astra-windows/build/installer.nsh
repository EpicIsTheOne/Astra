!macro preInit
  SetShellVarContext current
  StrCpy $INSTDIR "$DOCUMENTS\Astra"
!macroend

!macro customInstall
  CreateDirectory "$INSTDIR"
!macroend
