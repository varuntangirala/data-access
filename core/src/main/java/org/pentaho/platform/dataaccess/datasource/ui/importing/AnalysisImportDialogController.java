/*!
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2002-2021 Hitachi Vantara..  All rights reserved.
 */

package org.pentaho.platform.dataaccess.datasource.ui.importing;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteHandler;
import com.google.gwt.user.client.ui.FormPanel.SubmitEvent;
import com.google.gwt.user.client.ui.Hidden;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.autobean.shared.AutoBean;
import com.google.web.bindery.autobean.shared.AutoBeanCodex;
import org.pentaho.database.model.IDatabaseConnection;
import org.pentaho.gwt.widgets.client.utils.NameUtils;
import org.pentaho.gwt.widgets.client.utils.i18n.ResourceBundle;
import org.pentaho.gwt.widgets.client.utils.string.StringUtils;
import org.pentaho.mantle.client.csrf.CsrfUtil;
import org.pentaho.mantle.client.csrf.JsCsrfToken;
import org.pentaho.platform.dataaccess.datasource.IDatasourceInfo;
import org.pentaho.platform.dataaccess.datasource.utils.DataSourceInfoUtil;
import org.pentaho.platform.dataaccess.datasource.wizard.DatasourceMessages;
import org.pentaho.platform.dataaccess.datasource.wizard.controllers.MessageHandler;
import org.pentaho.ui.database.event.IConnectionAutoBeanFactory;
import org.pentaho.ui.database.event.IDatabaseConnectionList;
import org.pentaho.ui.xul.XulComponent;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingConvertor;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.components.XulButton;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.components.XulLabel;
import org.pentaho.ui.xul.components.XulMenuList;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulRadio;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.containers.XulDeck;
import org.pentaho.ui.xul.containers.XulDialog;
import org.pentaho.ui.xul.containers.XulTree;
import org.pentaho.ui.xul.containers.XulVbox;
import org.pentaho.ui.xul.gwt.tags.GwtTree;
import org.pentaho.ui.xul.stereotype.Bindable;
import org.pentaho.ui.xul.util.AbstractXulDialogController;
import org.pentaho.ui.xul.util.XulDialogCallback;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings( "all" )
public class AnalysisImportDialogController extends AbstractXulDialogController<AnalysisImportDialogModel> implements
  IImportPerspective, IOverwritableController {

  private static final String MONDRIAN_POSTANALYSIS_URL = "plugin/data-access/api/mondrian/postAnalysis";

  public static final String ATTRIBUTE_STANDARD_CONNECTION = "STANDARD_CONNECTION"; //$NON-NLS-1$

  /**
   * The name of the CSRF token field to use when CSRF protection is disabled.
   * <p>
   * An arbitrary name, yet different from the name it can have when CSRF protection enabled.
   * This avoids not having to dynamically adding and removing the field from the form depending
   * on whether CSRF protection is enabled or not.
   * <p>
   * When CSRF protection is enabled,
   * the actual name of the field is set before each submit.
   */
  private static final String DISABLED_CSRF_TOKEN_PARAMETER = "csrf_token_disabled";

  private static Logger logger = Logger.getLogger( AnalysisImportDialogController.class.getName() );

  private BindingFactory bf;

  private XulMenuList connectionList;

  private GwtTree analysisParametersTree;

  private XulDialog importDialog;

  private XulDialog analysisParametersDialog;

  private ResourceBundle resBundle;

  private AnalysisImportDialogModel importDialogModel;

  //  private IXulAsyncConnectionService connectionService;

  private XulTextbox paramNameTextBox;

  private XulTextbox paramValueTextBox;

  private XulDeck analysisPreferencesDeck;

  private XulRadio availableRadio;

  private XulRadio manualRadio;

  private XulButton acceptButton;

  private XulButton parametersAcceptButton;

  private FileUpload analysisUpload;

  private XulLabel schemaNameLabel;

  private XulLabel analysisFileLabel;

  private String importURL;

  private boolean overwrite = false;

  private static final Integer PARAMETER_MODE = 1;

  private static final Integer DATASOURCE_MODE = 0;

  protected static final String SUCCESS = "3"; //to do - chnage to Integer

  protected static final int PUBLISH_SCHEMA_EXISTS_ERROR = 8;

  protected static final int PUBLISH_SCHEMA_CATALOG_EXISTS_ERROR = 7;

  private static SubmitCompleteHandler submitHandler = null;

  private DatasourceMessages messages = null;

  private XulVbox hiddenArea;

  private FormPanel formPanel;

  private FlowPanel mainFormPanel;

  private FileUpload analysisFileUpload;

  private XulButton uploadAnalysisButton;

  /**
   * The CSRF token field/parameter.
   * Its name and value are set to the expected values before each submit,
   * to match the obtained {@link JsCsrfToken}.
   * <p>
   * The Tomcat's context must have the `allowCasualMultipartParsing` attribute set
   * so that the `CsrfGateFilter` is able to transparently read this parameter
   * in a multi-part encoding form, as is the case of `form`.
   */
  private Hidden csrfTokenParameter;

  private IDatasourceInfo datasourceInfo;

  protected IConnectionAutoBeanFactory connectionAutoBeanFactory;

  public void init() {
    try {
      connectionAutoBeanFactory = GWT.create( IConnectionAutoBeanFactory.class );
      resBundle = (ResourceBundle) super.getXulDomContainer().getResourceBundles().get( 0 );
      //      connectionService = new ConnectionServiceGwtImpl();
      importDialogModel = new AnalysisImportDialogModel();
      csrfTokenParameter = new Hidden( DISABLED_CSRF_TOKEN_PARAMETER );
      connectionList = (XulMenuList) document.getElementById( "connectionList" );
      analysisParametersTree = (GwtTree) document.getElementById( "analysisParametersTree" );
      importDialog = (XulDialog) document.getElementById( "importDialog" );
      analysisParametersDialog = (XulDialog) document.getElementById( "analysisParametersDialog" );
      paramNameTextBox = (XulTextbox) document.getElementById( "paramNameTextBox" );
      paramNameTextBox.addPropertyChangeListener( new ParametersChangeListener() );
      paramValueTextBox = (XulTextbox) document.getElementById( "paramValueTextBox" );
      paramValueTextBox.addPropertyChangeListener( new ParametersChangeListener() );
      analysisPreferencesDeck = (XulDeck) document.getElementById( "analysisPreferencesDeck" );
      availableRadio = (XulRadio) document.getElementById( "availableRadio" );
      manualRadio = (XulRadio) document.getElementById( "manualRadio" );
      hiddenArea = (XulVbox) document.getElementById( "analysisImportCard" );
      schemaNameLabel = (XulLabel) document.getElementById( "schemaNameLabel" );
      analysisFileLabel = (XulLabel) document.getElementById( "analysisFileLabel" );
      uploadAnalysisButton = (XulButton) document.getElementById( "uploadAnalysisButton" );
      acceptButton = (XulButton) document.getElementById( "importDialog_accept" );
      acceptButton.setDisabled( true );

      parametersAcceptButton = (XulButton) document.getElementById( "analysisParametersDialog_accept" );
      parametersAcceptButton.setDisabled( true );

      bf.setBindingType( Binding.Type.ONE_WAY );
      bf.createBinding( connectionList, "selectedIndex", importDialogModel, "connection",
        new BindingConvertor<Integer, IDatabaseConnection>() {
          @Override
          public Integer targetToSource( IDatabaseConnection connection ) {
            return -1;
          }

          @Override
          public IDatabaseConnection sourceToTarget( Integer value ) {
            if ( value >= 0 ) {
              return importDialogModel.getConnectionList().get( value );
            }
            return null;
          }
        } );

      bf.createBinding( manualRadio, "checked", this, "preference", new PreferencesBindingConvertor() );
      bf.createBinding( this, "connectionNames", connectionList, "elements" );

      Binding domainBinding = bf.createBinding( importDialogModel, "connectionList", this, "relationalConnections" );
      Binding analysisParametersBinding = bf.createBinding( importDialogModel, "analysisParameters",
        analysisParametersTree, "elements" );
      domainBinding.fireSourceChanged();
      analysisParametersBinding.fireSourceChanged();

    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  @Bindable
  public void setRelationalConnections( List<IDatabaseConnection> connections ) {
    List<String> names = new ArrayList<String>();
    for ( IDatabaseConnection conn : connections ) {
      names.add( conn.getName() );
    }

    firePropertyChange( "connectionNames", null, names );
  }

  public static String getBaseURL() {
    return getContextBaseURL() + "plugin/data-access/api/connection/";
  }

  private static String getContextBaseURL() {
    String moduleUrl = GWT.getModuleBaseURL();

    // Determine the base url appropriately based on the context in which we are running this client.
    if ( moduleUrl.indexOf( "content" ) > -1 ) {
      return moduleUrl.substring( 0, moduleUrl.indexOf( "content" ) );
    }

    return moduleUrl;
  }

  private void createWorkingForm() {
    if ( formPanel == null ) {
      formPanel = new FormPanel();
      formPanel.setMethod( FormPanel.METHOD_POST );
      formPanel.setEncoding( FormPanel.ENCODING_MULTIPART );
      formPanel.setAction( getContextBaseURL() + MONDRIAN_POSTANALYSIS_URL );

      formPanel.getElement().getStyle().setProperty( "position", "absolute" );
      formPanel.getElement().getStyle().setProperty( "visibility", "hidden" );
      formPanel.getElement().getStyle().setProperty( "overflow", "hidden" );
      formPanel.getElement().getStyle().setProperty( "clip", "rect(0px,0px,0px,0px)" );

      mainFormPanel = new FlowPanel();

      analysisFileUpload = new FileUpload();
      analysisFileUpload.setName( "uploadAnalysis" );
      analysisFileUpload.getElement().setId( "analysisFileUpload" );
      analysisFileUpload.addChangeHandler( new ChangeHandler() {
        @Override
        public void onChange( ChangeEvent event ) {
          schemaNameLabel.setValue( ( (FileUpload) event.getSource() ).getFilename() );
          importDialogModel.setUploadedFile( ( (FileUpload) event.getSource() ).getFilename() );
          acceptButton.setDisabled( !isValid() );
        }
      } );

      mainFormPanel.add( analysisFileUpload );

      mainFormPanel.add( csrfTokenParameter );

      formPanel.add( mainFormPanel );

      VerticalPanel vp = (VerticalPanel) hiddenArea.getManagedObject();
      vp.add( formPanel );
      //addSubmitHandler(); moved to GwtDataSourceEditorEntryPoint
    }
  }

  @Override @Bindable
  public void onDialogAccept() {
    hideDialog();

    setupCsrfToken();

    super.onDialogAccept();
  }

  /**
   * Obtains a CSRF token for the form's current URL and
   * fills it in the form's token parameter hidden field.
   */
  private void setupCsrfToken() {
    assert formPanel != null;

    JsCsrfToken token = CsrfUtil.getCsrfTokenSync( formPanel.getAction() );
    if ( token != null ) {
      csrfTokenParameter.setName( token.getParameter() );
      csrfTokenParameter.setValue( token.getToken() );
    } else {
      // Reset the field.
      csrfTokenParameter.setName( DISABLED_CSRF_TOKEN_PARAMETER );
      csrfTokenParameter.setValue( "" );
    }
  }

  /**
   * Initialize this in the form init() return values are numeric -
   */

  private void addSubmitHandler() {

    if ( submitHandler == null ) {
      formPanel.addSubmitHandler( new FormPanel.SubmitHandler() {
        @Override
        public void onSubmit( SubmitEvent event ) {

        }
      } );
      submitHandler = new FormPanel.SubmitCompleteHandler() {

        @Override
        public void onSubmitComplete( SubmitCompleteEvent event ) {
          handleFormPanelEvent( event );
        }

      };
      formPanel.addSubmitCompleteHandler( submitHandler );
    }
  }

  /**
   * Called by importDialog XUL file.  When user selects a schema file from File Browser then this is the callback to
   * set the file.  We need to call a native method to simulate a click on the file browser control.
   */
  @Bindable
  public void setAnalysisFile() {
    jsClickUpload( analysisFileUpload.getElement().getId() );
  }

  native void jsClickUpload( String uploadElement ) /*-{
    $doc.getElementById(uploadElement).click();
  }-*/;

  @Bindable
  public void setSelectedFile( String name ) {
    schemaNameLabel.setValue( name );
    importDialogModel.setUploadedFile( name );

    firePropertyChange( "selectedFile", null, name ); //$NON-NLS-1$
  }

  public XulDialog getDialog() {
    return importDialog;
  }

  public AnalysisImportDialogModel getDialogResult() {
    return importDialogModel;
  }

  private void reset() {
    analysisFileLabel.setValue( resBundle.getString( "importDialog.IMPORT_MONDRIAN", "Browse for analysis file" ) );

    if ( formPanel != null && RootPanel.get().getWidgetIndex( formPanel ) != -1 ) {
      RootPanel.get().remove( formPanel );
    }
    formPanel = null;
    submitHandler = null;

    reloadConnections();
    importDialogModel.removeAllParameters();
    importDialogModel.setUploadedFile( null );
    availableRadio.setSelected( true );
    acceptButton.setDisabled( true );
    schemaNameLabel.setValue( "" );
    csrfTokenParameter.setValue( "" );
    setPreference( DATASOURCE_MODE );
    overwrite = false;
    removeHiddenPanels();
  }

  public void removeHiddenPanels() {
    // Remove all previous hidden form parameters otherwise parameters
    // from a previous import would get included in current form submit         
    List<Widget> hiddenPanels = findHiddenPanels();
    for ( Widget hiddenPanel : hiddenPanels ) {
      mainFormPanel.remove( hiddenPanel );
    }
  }

  /**
   * create a List of hidden panels
   *
   * @return Widget list or empty
   */
  private List<Widget> findHiddenPanels() {
    ArrayList<Widget> hiddenPanels = new ArrayList<Widget>();
    if ( mainFormPanel != null ) {
      int widgetCount = mainFormPanel.getWidgetCount();
      for ( int i = 0; i < widgetCount; i++ ) {
        Widget widget = mainFormPanel.getWidget( i );
        if ( widget.getClass().equals( Hidden.class ) && widget != csrfTokenParameter ) {
          hiddenPanels.add( mainFormPanel.getWidget( i ) );
        }
      }
    }

    return hiddenPanels;
  }

  private void reloadConnections() {
    String cacheBuster = "?ts=" + new java.util.Date().getTime();
    RequestBuilder listConnectionBuilder =
      new RequestBuilder( RequestBuilder.GET, getBaseURL() + "list" + cacheBuster );

    listConnectionBuilder.setHeader( "Content-Type", "application/json" );
    listConnectionBuilder.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );
    try {
      listConnectionBuilder.sendRequest( null, new RequestCallback() {

        @Override
        public void onError( Request request, Throwable exception ) {
          exception.printStackTrace();
          MessageHandler.getInstance().showErrorDialog( exception.getMessage() );
        }

        @Override
        public void onResponseReceived( Request request, Response response ) {
          AutoBean<IDatabaseConnectionList> bean = AutoBeanCodex.decode( connectionAutoBeanFactory,
            IDatabaseConnectionList.class, response.getText() );
          List<IDatabaseConnection> databaseConnections = bean.as().getDatabaseConnections();
          List<IDatabaseConnection> standardDatabaseConnections = new ArrayList();

          // take anything except connections where STANDARD_CONNECTION == false
          for ( IDatabaseConnection databaseConnection : databaseConnections ) {
            if ( ( databaseConnection.getAttributes() == null )
              || ( databaseConnection.getAttributes().get( ATTRIBUTE_STANDARD_CONNECTION ) == null )
              || ( databaseConnection.getAttributes().get( ATTRIBUTE_STANDARD_CONNECTION )
              == Boolean.TRUE.toString() ) ) {
              standardDatabaseConnections.add( databaseConnection );
            }

          }

          importDialogModel.setConnectionList( standardDatabaseConnections );
        }
      } );
    } catch ( RequestException e ) {
      MessageHandler.getInstance().showErrorDialog( MessageHandler.getString( "ERROR" ),
        "DatasourceEditor.ERROR_0004_CONNECTION_SERVICE_NULL" );
    }
  }

  public boolean isValid() {
    return importDialogModel.isValid();
  }

  public void handleFormPanelEvent( SubmitCompleteEvent event ) {
    if ( event.getResults().contains( "SUCCESS" ) || event.getResults().contains( "3" ) ) {
      showMessagebox( messages.getString( "Mondrian.SUCCESS" ),
        "Mondrian Analysis File " + importDialogModel.getUploadedFile() + " has been uploaded" );
      overwrite = false;
    } else {
      String message = event.getResults();
      //message = message.substring(4, message.length() - 6);
      if ( message != null && !"".equals( message ) && message.length() == 1 ) {
        int code = new Integer( message ).intValue();
        if ( !overwrite ) {
          overwriteFileDialog( code );
        } else {
          showMessagebox( messages.getString( "Mondrian.ERROR" ),
            convertToNLSMessage( event.getResults(), importDialogModel.getUploadedFile() ) );
        }
      } else {
        showMessagebox( messages.getString( "Mondrian.SERVER_ERROR" ),
          convertToNLSMessage( event.getResults(), importDialogModel.getUploadedFile() ) );
      }
    }
  }

  /**
   * Convert to $NLS$
   *
   * @param results
   * @return msg int PUBLISH_TO_SERVER_FAILED = 1; int PUBLISH_GENERAL_ERROR = 2; int PUBLISH_DATASOURCE_ERROR = 6; int
   * PUBLISH_USERNAME_PASSWORD_FAIL = 5; int PUBLISH_XMLA_CATALOG_EXISTS = 7; int PUBLISH_SCHEMA_EXISTS_ERROR = 8;
   */
  public String convertToNLSMessage( String results, String fileName ) {
    String msg = results;
    int code = new Integer( results ).intValue();
    switch ( code ) {
      case 1:
        msg = messages.getString( "Mondrian.ERROR_OO1_PUBLISH" );
        break;
      case 2:
        msg = messages.getString( "Mondrian.ERROR_OO2_PUBLISH" );
        break;
      case 5:
        msg = messages.getString( "Mondrian.ERROR_OO5_USERNAME_PW" );
        break;
      case 6:
        msg = messages.getString( "Mondrian.ERROR_OO6_Existing_Datasource" );
        break;
      case 7:
        msg = messages.getString( "Mondrian.OVERWRITE_EXISTING_CATALOG" );
        break;
      case 8:
        msg = messages.getString( "Mondrian.OVERWRITE_EXISTING_SCHEMA" );
        break;
      default:
        msg = messages.getString( "Mondrian.General Error", results );
        break;
    }
    return msg + " Mondrian File: " + fileName;
  }

  public void buildAndSetParameters() {
    buildAndSetParameters( false );
  }

  public void buildAndSetParameters( boolean isEditMode ) {

    if ( isEditMode ) {
      String file = importDialogModel.getUploadedFile();
      if ( file != null ) {
        mainFormPanel.add( new Hidden( "catalogName", file ) );
      }
      // MONDRIAN-1731
      if ( datasourceInfo != null ) {
        mainFormPanel.add( new Hidden( "origCatalogName", datasourceInfo.getId() ) );
      }
    }

    // If user selects available data source, then pass the datasource as part of the parameters.
    // If user selects manual data source, pass in whatever parameters they specify even if it is empty.
    String parameters = importDialogModel.getParameters();
    if ( availableRadio.isSelected() ) {
      parameters = datasourceParam( connectionList.getValue() );
    }
    // Parameters would contain either the data source from connectionList drop-down
    // or the parameters manually entered (even if list is empty)
    String sep = ( StringUtils.isEmpty( parameters ) ) ? "" : ";";
    parameters += ";overwrite=" + String.valueOf( isEditMode ? isEditMode : overwrite );
    Hidden queryParameters = new Hidden( "parameters", parameters );
    mainFormPanel.add( queryParameters );
  }

  protected String datasourceParam( String datasourceName ) {
    return "Datasource=\"" + datasourceName + "\"";
  }

  // TODO - this method should be removed after it is removed by MetadataImportDialogController
  public void concreteUploadCallback( String fileName, String uploadedFile ) {
    acceptButton.setDisabled( !isValid() );
  }

  // TODO - this method should be removed after it is removed by MetadataImportDialogController
  public void genericUploadCallback( String uploadedFile ) {
    importDialogModel.setUploadedFile( uploadedFile );
    acceptButton.setDisabled( !isValid() );
  }

  @Bindable
  public void setPreference( Integer preference ) {
    analysisPreferencesDeck.setSelectedIndex( preference );
    if ( preference == PARAMETER_MODE ) {
      // Make sure UI is updated after becoming visible.
      analysisParametersTree.updateUI();
    }

    importDialogModel.setParameterMode( preference == PARAMETER_MODE );
    acceptButton.setDisabled( !isValid() );
  }

  @Bindable
  public void removeParameter() {
    int[] selectedRows = analysisParametersTree.getSelectedRows();
    if ( selectedRows.length == 1 ) {
      importDialogModel.removeParameter( selectedRows[ 0 ] );
      acceptButton.setDisabled( !isValid() );
    }
  }

  @Bindable
  public void addParameter() {
    String paramName = paramNameTextBox.getValue();
    String paramValue = paramValueTextBox.getValue();
    if ( !StringUtils.isEmpty( paramName ) && !StringUtils.isEmpty( paramValue ) ) {
      importDialogModel.addParameter( paramName, paramValue );
      closeParametersDialog();
      acceptButton.setDisabled( !isValid() );
    }
  }

  @Bindable
  public void closeParametersDialog() {
    analysisParametersDialog.hide();
    paramNameTextBox.setValue( "" );
    paramValueTextBox.setValue( "" );
    importDialogModel.setSelectedAnalysisParameter( -1 );
    analysisParametersTree.clearSelection();
  }

  @Bindable
  public void editParameter() {
    int[] selectedRows = analysisParametersTree.getSelectedRows();
    if ( selectedRows.length == 1 ) {
      importDialogModel.setSelectedAnalysisParameter( selectedRows[ 0 ] );
      ParameterDialogModel parameter = importDialogModel.getSelectedAnalysisParameter();
      paramNameTextBox.setValue( parameter.getName() );
      paramValueTextBox.setValue( parameter.getValue() );
      analysisParametersDialog.show();
    }
  }

  @Bindable
  public void overwriteFileDialog( int code ) {
    if ( code != PUBLISH_SCHEMA_CATALOG_EXISTS_ERROR && code != PUBLISH_SCHEMA_EXISTS_ERROR ) {
      return;
    }
    String msg = messages.getString( "Mondrian.OVERWRITE_EXISTING_SCHEMA" );
    if ( PUBLISH_SCHEMA_CATALOG_EXISTS_ERROR == code ) {
      msg = messages.getString( "Mondrian.OVERWRITE_EXISTING_CATALOG" );
    }
    XulConfirmBox confirm = null;
    try {
      confirm = (XulConfirmBox) document.createElement( "confirmbox" );
    } catch ( XulException e ) {
      Window.alert( e.getMessage() );
    }

    confirm.setTitle( "Confirmation" );
    confirm.setMessage( msg );
    confirm.setAcceptLabel( "Ok" );
    confirm.setCancelLabel( "Cancel" );
    confirm.addDialogCallback( new XulDialogCallback<String>() {
      public void onClose( XulComponent component, Status status, String value ) {
        if ( status == XulDialogCallback.Status.ACCEPT ) {
          overwrite = true;
          removeHiddenPanels();
          buildAndSetParameters();
          formPanel.submit();
        }
      }

      public void onError( XulComponent component, Throwable err ) {
        return;
      }
    } );
    confirm.open();
  }

  @Bindable
  public void openParametersDialog() {
    analysisParametersDialog.show();
  }

  public void showDialog() {
    reset();
    importDialog.setTitle( resBundle.getString( "importDialog.IMPORT_MONDRIAN", "Import Analysis" ) );
    analysisFileLabel.setValue( resBundle.getString( "importDialog.MONDRIAN_FILE", "Mondrian File" ) + ":" );
    super.showDialog();
    createWorkingForm();
  }

  public void setBindingFactory( final BindingFactory bf ) {
    this.bf = bf;
  }

  public String getName() {
    return "analysisImportDialogController";
  }

  class PreferencesBindingConvertor extends BindingConvertor<Boolean, Integer> {

    public Integer sourceToTarget( Boolean value ) {
      int result = 0;
      if ( value ) {
        result = 1;
      }
      return result;
    }

    public Boolean targetToSource( Integer value ) {
      return true;
    }
  }

  class ParametersChangeListener implements PropertyChangeListener {

    public void propertyChange( PropertyChangeEvent evt ) {
      boolean isDisabled = StringUtils.isEmpty( paramNameTextBox.getValue() )
        || StringUtils.isEmpty( paramValueTextBox.getValue() );
      parametersAcceptButton.setDisabled( isDisabled );
    }
  }

  /**
   * Shows a informational dialog.
   *
   * @param title   title of dialog
   * @param message message within dialog
   */
  private void showMessagebox( final String title, final String message ) {
    try {
      XulMessageBox messagebox = (XulMessageBox) document.createElement( "messagebox" ); //$NON-NLS-1$

      messagebox.setTitle( title );
      messagebox.setMessage( message );
      int option = messagebox.open();
    } catch ( XulException e ) {
      Window.alert( "Show MessabeBox " + e.getMessage() );
    }

  }

  /**
   * pass localized messages from Entry point initialization
   *
   * @param datasourceMessages
   */
  public void setDatasourceMessages( DatasourceMessages datasourceMessages ) {
    this.messages = datasourceMessages;
  }

  /**
   * allow the GWT Entry to call this panel for onSubmit call
   *
   * @return
   */
  public FormPanel getFormPanel() {
    return formPanel;
  }

  /**
   * helper method for dialog display
   *
   * @return
   */
  public String getFileName() {
    return this.importDialogModel.getUploadedFile();
  }

  public void setOverwrite( boolean overwrite ) {
    this.overwrite = overwrite;
  }

  protected boolean handleParam( StringBuilder name, StringBuilder value ) {
    if ( name.length() == 0 && value.length() == 0 ) {
      return false;
    }
    boolean hasParameters = false;
    boolean connectionFound = false;
    String paramName = name.toString();
    //Unescape quotes is used, because value can contain &quot; elements.
    String paramValue = DataSourceInfoUtil.unescapeQuotes( value.toString() );
    if ( paramName.equalsIgnoreCase( "Datasource" ) ) {
      for ( IDatabaseConnection connection : importDialogModel.getConnectionList() ) {
        if ( connection.getName().equals( paramValue ) ) {
          importDialogModel.setConnection( connection );
          connectionFound = true;
        }
      }
      //always add the Datasource so if the toggle is selected it displays - 
      // it may be JNDI and not in DSW
      importDialogModel.addParameter( paramName, paramValue );
      hasParameters = !connectionFound;
    } else {
      if ( !paramName.equalsIgnoreCase( "overwrite" ) && !paramName.equalsIgnoreCase( "Provider" ) ) {
        importDialogModel.addParameter( paramName, paramValue );
        //this is the default value so do not treat it as a param to flip to manual mode
        if ( ( paramName.equalsIgnoreCase( "EnableXmla" ) && paramValue.equalsIgnoreCase( "true" ) ) ) {
          hasParameters = false;
        } else {
          hasParameters = true;
        }
      }
    }
    name.setLength( 0 );
    value.setLength( 0 );
    return hasParameters;
  }

  public void editDatasource( final IDatasourceInfo datasourceInfo ) {

    this.datasourceInfo = datasourceInfo;
    boolean isEditMode = datasourceInfo != null;
    // MONDRIAN-1731 uploadAnalysisButton.setDisabled(isEditMode);
    acceptButton.setLabel( isEditMode ? resBundle.getString( "importDialog.SAVE" ) : resBundle
      .getString( "importDialog.IMPORT" ) );

    if ( !isEditMode ) {
      return;
    }

    String url = GWT.getModuleBaseURL();
    int indexOfContent = url.indexOf( "content" );
    if ( indexOfContent > -1 ) {
      url = url.substring( 0, indexOfContent );
      url = url + "plugin/data-access/api/datasource/" + NameUtils.URLEncode( datasourceInfo.getId() )
        + "/getAnalysisDatasourceInfo";
    }
    RequestBuilder requestBuilder = new RequestBuilder( RequestBuilder.GET, url );
    requestBuilder.setHeader( "If-Modified-Since", "01 Jan 1970 00:00:00 GMT" );
    try {
      requestBuilder.sendRequest( null, new RequestCallback() {

        public void onError( Request request, Throwable e ) {
          logger.log( Level.ALL, e.getMessage() );
        }

        public void onResponseReceived( Request request, final Response response ) {

          boolean paramHandled, hasParameters = false;
          String responseValue = response.getText();
          StringBuilder name = new StringBuilder();
          StringBuilder value = new StringBuilder();
          int state = 0;
          char ch;
          int i, len = responseValue.length();
          for ( i = 0; i < len; i++ ) {
            ch = responseValue.charAt( i );
            switch ( state ) {
              case 0: //new name/value pair
                paramHandled = handleParam( name, value );
                if ( !hasParameters ) {
                  hasParameters = paramHandled;
                }
                switch ( ch ) {
                  case ';':
                    break;
                  default:
                    state = 1;
                    name.append( ch );
                }
                break;
              case 1: //looking for equals
                switch ( ch ) {
                  case '=':
                    state = 2;
                    break;
                  default:
                    name.append( ch );
                }
                break;
              case 2: //about to parse the value
                switch ( ch ) {
                  case '"':
                    state = 3;
                    break;
                  case ';':
                    state = 0;
                    break;
                  default:
                    value.append( ch );
                    state = 4;
                }
                break;
              case 3: //parse value till closing quote.
                switch ( ch ) {
                  case '"':
                    state = 0;
                    break;
                  default:
                    value.append( ch );
                }
                break;
              case 4:
                switch ( ch ) {
                  case ';':
                    state = 0;
                    break;
                  default:
                    value.append( ch );
                }
                break;
              default:

            }
          }
          paramHandled = handleParam( name, value );
          if ( !hasParameters ) {
            hasParameters = paramHandled;
          }

          schemaNameLabel.setValue( datasourceInfo.getId() + ".mondrian.xml" );
          importDialogModel.setUploadedFile( datasourceInfo.getId() );

          int preference;
          XulRadio radio;
          if ( hasParameters ) {
            preference = PARAMETER_MODE;
            radio = manualRadio;
          } else {
            preference = DATASOURCE_MODE;
            radio = availableRadio;
          }
          setPreference( preference );
          radio.setSelected( true );
        }
      } );
    } catch ( Exception e ) {
      logger.log( Level.ALL, e.getMessage() );
    }
  }

}
