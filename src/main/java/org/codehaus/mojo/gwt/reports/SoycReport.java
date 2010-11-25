package org.codehaus.mojo.gwt.reports;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.surefire.booter.shade.org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.mojo.gwt.AbstractGwtMojo;
import org.codehaus.mojo.gwt.ClasspathBuilder;
import org.codehaus.mojo.gwt.GwtModule;
import org.codehaus.mojo.gwt.GwtModuleReader;
import org.codehaus.mojo.gwt.shell.JavaCommand;
import org.codehaus.mojo.gwt.shell.JavaCommandException;
import org.codehaus.mojo.gwt.shell.JavaCommandRequest;
import org.codehaus.mojo.gwt.utils.DefaultGwtModuleReader;
import org.codehaus.mojo.gwt.utils.GwtDevHelper;
import org.codehaus.mojo.gwt.utils.GwtModuleReaderException;

/**
 * @see http://code.google.com/p/google-web-toolkit/wiki/CodeSplitting#The_Story_of_Your_Compile_(SOYC)
 * @goal soyc
 * @phase site
 */
public class SoycReport
    extends AbstractMavenReport
{

    /**
     * The output directory of the jsdoc report.
     *
     * @parameter expression="${project.reporting.outputDirectory}/soyc"
     * @required
     * @readonly
     */
    protected File reportingOutputDirectory;

    /**
     * The directory into which extra, non-deployed files will be written.
     *
     * @parameter default-value="${project.build.directory}/extra"
     */
    private File extra;
    
    /**
     * Doxia Site Renderer component.
     *
     * @component
     * @since 2.1.1
     */
    protected Renderer siteRenderer;  
    
    /**
     * The output directory for the report. Note that this parameter is only evaluated if the goal is run directly from
     * the command line. If the goal is run indirectly as part of a site generation, the output directory configured in
     * the Maven Site Plugin is used instead.
     *
     * @parameter expression="${project.reporting.outputDirectory}"
     * @required
     * @since 2.1.1
     */    
    protected File outputDirectory;
    
    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     * @since 2.1.1
     */
    protected MavenProject project;    
    
    /**
     * @parameter expression="${plugin.artifactMap}"
     * @since 2.1.1
     */
    private Map<String, Artifact> pluginArtifacts;    
    
    /**
     * @component
     * @since 2.1.1
     */
    protected ClasspathBuilder classpathBuilder;    

    /**
     * {@inheritDoc}
     *
     * @see org.apache.maven.reporting.MavenReport#canGenerateReport()
     */
    public boolean canGenerateReport()
    {
        // TODO check the compiler has created the raw xml soyc file
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.maven.reporting.MavenReport#getCategoryName()
     */
    public String getCategoryName()
    {
        return CATEGORY_PROJECT_REPORTS;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.maven.reporting.MavenReport#getDescription(java.util.Locale)
     */
    public String getDescription( Locale locale )
    {
        return "GWT Story Of Your Compiler";
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.maven.reporting.MavenReport#getName(java.util.Locale)
     */
    public String getName( Locale locale )
    {
        return "GWT Story Of Your Compiler";
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.maven.reporting.MavenReport#getOutputName()
     */
    public String getOutputName()
    {
        return "soyc";
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.maven.reporting.MavenReport#getReportOutputDirectory()
     */
    public File getReportOutputDirectory()
    {
        return reportingOutputDirectory;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.maven.reporting.MavenReport#isExternalReport()
     */
    public boolean isExternalReport()
    {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.maven.reporting.MavenReport#setReportOutputDirectory(java.io.File)
     */
    public void setReportOutputDirectory( File outputDirectory )
    {
        reportingOutputDirectory = outputDirectory;
    }

    @Override
    protected Renderer getSiteRenderer()
    {
        return siteRenderer;
    }

    @Override
    protected String getOutputDirectory()
    {
        return outputDirectory.getAbsolutePath();
    }

    @Override
    protected MavenProject getProject()
    {
        return project;
    }

    @Override
    protected void executeReport( Locale locale )
        throws MavenReportException
    {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( extra );
        scanner.setIncludes( new String[] { "**/soycReport/stories0.xml.gz" } );
        scanner.scan();

        if ( scanner.getIncludedFiles().length == 0 )
        {
            getLog().warn( "No SOYC raw report found, did you compile with soyc option set ?" );
            return;
        }
        
        GwtDevHelper gwtDevHelper = new GwtDevHelper( pluginArtifacts, project, getLog(), AbstractGwtMojo.GWT_GROUP_ID );
        String[] includeFiles = scanner.getIncludedFiles();

        for ( String path : includeFiles )
        {
            try
            {
                //Usage: java com.google.gwt.soyc.SoycDashboard -resources dir -soycDir dir -symbolMaps dir [-out dir]
                String module = path.substring( 0, path.indexOf( File.separatorChar ) );
                JavaCommandRequest javaCommandRequest = new JavaCommandRequest()
                    .setClassName( "com.google.gwt.soyc.SoycDashboard" )
                    .setLog( getLog() );
                JavaCommand cmd = new JavaCommand( javaCommandRequest ).withinClasspath( gwtDevHelper.getGwtDevJar() )
                //  FIXME
                // .withinClasspath( runtime.getSoycJar() )
                //.arg( "-resources" ).arg( runtime.getSoycJar().getAbsolutePath() )
                    .arg( "-out" ).arg( reportingOutputDirectory.getAbsolutePath() + File.separatorChar + module );

                cmd.arg( new File( extra, path ).getAbsolutePath() );
                cmd.arg( new File( extra, path ).getAbsolutePath().replace( "stories", "dependencies" ) );
                cmd.arg( new File( extra, path ).getAbsolutePath().replace( "stories", "splitPoints" ) );
                cmd.execute();
            }
            catch ( IOException e )
            {
                throw new MavenReportException( e.getMessage(), e );
            }
            catch ( JavaCommandException e )
            {
                throw new MavenReportException( e.getMessage(), e );
            }
        }
        // TODO use this in the report generation instead of file scanning
        try
        {

            GwtModuleReader gwtModuleReader = new DefaultGwtModuleReader( this.project, getLog(), classpathBuilder );

            List<GwtModule> gwtModules = new ArrayList<GwtModule>();
            List<String> moduleNames = gwtModuleReader.getGwtModules();
            for ( String name : moduleNames )
            {
                gwtModules.add( gwtModuleReader.readModule( name ) );
            }
            // add link in the page to all module reports
            CompilationReportRenderer compilationReportRenderer = new CompilationReportRenderer( getSink(),
                                                                                                 gwtModules, getLog() );
            compilationReportRenderer.render();
        }
        catch ( GwtModuleReaderException e )
        {
            throw new MavenReportException( e.getMessage(), e );
        }
    }

}