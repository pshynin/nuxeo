<html>
<head>
    <style>
        div {
            margin: 0;
            padding: 0;
            background-color: #e9ecef;
            font-family: Arial, sans-serif;
            marginheight: 0;
            marginwidth: 0;
        }

        table {
            margin: 0;
            padding: 0;
            border: none;
        }

        td {
            border-collapse: collapse;
            margin: 0;
            padding: 20px;
            border-top: 0;
            align-content: center;
            vertical-align: top;
            border: 1px solid #eee;
            color: #000;
            font-size: 13px;
        }

        p {
            font-size: 13px;
        }

        .propertyName {
            color: #888;
        }

        .a {
            color: #22aee8;
            text-decoration: underline;
            word-wrap: break-word !important;
        }
    </style>
</head>
<body>
<div>
    <br/>
    <p>The document ${htmlEscape(docTitle)} is now available.</p>
    <table>
        <tbody>
        <tr>
            <td class="propertyName">Document</td>
            <td>
                <a href="${docUrl}">${htmlEscape(docTitle)}</a>
            </td>
        </tr>
        <tr>
            <td class="propertyName">Author</td>
            <td>
                <a href="${userUrl}">
                    <#if principalAuthor?? && (principalAuthor.lastName!="" || principalAuthor.firstName!="")>
                        ${htmlEscape(principalAuthor.firstName)} ${htmlEscape(principalAuthor.lastName)}
                    </#if>
                    (${author})
                </a>
            </td>
        </tr>
        <tr>
            <td class="propertyName">Updated</td>
            <td>
                ${dateTime?datetime?string("dd/MM/yyyy - HH:mm")}
            </td>
        </tr>
        <tr>
            <td class="propertyName">Created</td>
            <td>
                ${docCreated?datetime?string("dd/MM/yyyy - HH:mm")}
            </td>
        </tr>
        <tr>
            <td class="propertyName">Location</td>
            <td>
                ${docLocation}
            </td>
        </tr>
        <tr>
            <td class="propertyName">State</td>
            <td>
                ${docState}
            </td>
        </tr>
        <tr>
            <td class="propertyName">Version</td>
            <td>
                ${docVersion}
            </td>
        </tr>
        </tbody>
    </table>
    <p style="margin:0">
        <a href="${docUrl}">&#187; Consult the document ${htmlEscape(docTitle)}</a>
    </p>
    <div>
        You received this notification because you subscribed to dummy notification on this
        document or on one of its parents.
    </div>
</div>
</body>
</html>
